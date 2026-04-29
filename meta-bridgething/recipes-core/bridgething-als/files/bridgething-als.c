/*
 * bridgething-als - TMD2772 ambient light → pwm-backlight bridge.
 *
 * Polls the clear-photodiode raw count from the IIO node every POLL_MS,
 * EMA-smooths it, maps via log10 to a brightness value, writes to
 * /sys/class/backlight/backlight/brightness when the result has moved
 * by HYSTERESIS or more.
 *
 * Why log10: human eye perception of brightness is approximately
 * logarithmic, and the photodiode count likewise spans 4+ decades from
 * dim ambient (~10) to direct flashlight (~10000). A linear mapping
 * would make tiny dim changes huge and bright changes invisible.
 *
 * Why hysteresis: every brightness write reprograms the PWM controller
 * (pwm_apply_state), which can briefly glitch the LED driver if the
 * caller fires a tight burst of small changes. HYSTERESIS=2 means the
 * smallest user-visible step (one BACKLIGHT level out of MAX) only
 * fires after the EMA has actually moved.
 *
 * Why ch0 (clear) not lux: the in_kernel tsl2772 lux table needs a
 * per-board calibration we don't have; the driver currently overflows
 * the lux value to TSL2772_LUX_CALC_OVER_FLOW (65535) with the default
 * table on the Superbird's panel cover glass. The raw clear-photodiode
 * count is what the chip actually measures and is monotonic with
 * incident light, which is all this bridge needs.
 */

#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define ALS_PATH "/sys/bus/iio/devices/iio:device0/in_intensity0_raw"
#define BL_PATH "/sys/class/backlight/backlight/brightness"
#define MAX_BR_PATH "/sys/class/backlight/backlight/max_brightness"

/* Defaults - all tunable via env vars (see /etc/bridgething-als.conf).
 * The chip is rated for ALS but in this enclosure the cover glass cuts
 * a lot of light, so the practical raw range is ~0 (covered) to ~500
 * (direct flashlight an inch away). RAW_AT_MAX=500 maps that range
 * across the full brightness span.
 */
#define DEFAULT_MIN_BRIGHTNESS                                                 \
  8 /* below this the LP8556 LED driver cuts out                               \
     */
#define DEFAULT_POLL_MS 200
#define DEFAULT_EMA_ALPHA 0.20 /* lower = smoother, higher = snappier */
#define DEFAULT_HYSTERESIS 2
#define DEFAULT_RAW_AT_MAX 500.0 /* raw count where brightness pegs at MAX */
#define DEFAULT_ZERO_STREAK_LIMIT                                              \
  5 /* spurious 0s ignored unless this many in a row */

static int min_brightness = DEFAULT_MIN_BRIGHTNESS;
static int poll_ms = DEFAULT_POLL_MS;
static double ema_alpha = DEFAULT_EMA_ALPHA;
static int hysteresis = DEFAULT_HYSTERESIS;
static double raw_at_max = DEFAULT_RAW_AT_MAX;
static int zero_streak_limit = DEFAULT_ZERO_STREAK_LIMIT;

static volatile sig_atomic_t running = 1;

static void sig(int s) {
  (void)s;
  running = 0;
}

static int read_int(const char *path) {
  int fd = open(path, O_RDONLY);
  if (fd < 0)
    return -1;
  char buf[32] = {0};
  int n = read(fd, buf, sizeof(buf) - 1);
  close(fd);
  if (n <= 0)
    return -1;
  return atoi(buf);
}

static int write_int(const char *path, int v) {
  int fd = open(path, O_WRONLY);
  if (fd < 0)
    return -1;
  char buf[32];
  int n = snprintf(buf, sizeof(buf), "%d\n", v);
  int w = write(fd, buf, n);
  close(fd);
  return w == n ? 0 : -1;
}

static void load_env(void) {
  const char *e;
  if ((e = getenv("ALS_MIN_BRIGHTNESS")) && atoi(e) > 0)
    min_brightness = atoi(e);
  if ((e = getenv("ALS_POLL_MS")) && atoi(e) > 0)
    poll_ms = atoi(e);
  if ((e = getenv("ALS_EMA_ALPHA")) && atof(e) > 0)
    ema_alpha = atof(e);
  if ((e = getenv("ALS_HYSTERESIS")) && atoi(e) > 0)
    hysteresis = atoi(e);
  if ((e = getenv("ALS_RAW_AT_MAX")) && atof(e) > 0)
    raw_at_max = atof(e);
  if ((e = getenv("ALS_ZERO_STREAK_LIMIT")) && atoi(e) > 0)
    zero_streak_limit = atoi(e);
}

int main(void) {
  struct sigaction sa = {.sa_handler = sig};
  sigaction(SIGTERM, &sa, NULL);
  sigaction(SIGINT, &sa, NULL);
  sigaction(SIGHUP, &sa, NULL);

  load_env();

  int max_brightness = read_int(MAX_BR_PATH);
  if (max_brightness <= 0) {
    fprintf(stderr, "bridgething-als: cannot read %s (errno %d)\n", MAX_BR_PATH,
            errno);
    return 1;
  }
  fprintf(stderr,
          "bridgething-als: max=%d min=%d poll=%dms alpha=%.2f "
          "hyst=%d raw_at_max=%.0f zero_streak_limit=%d\n",
          max_brightness, min_brightness, poll_ms, ema_alpha, hysteresis,
          raw_at_max, zero_streak_limit);

  double smoothed = -1.0; /* initialized on first sample */
  int last_brightness = -1;
  double log_max = log10(1.0 + raw_at_max);

  while (running) {
    int raw = read_int(ALS_PATH);
    if (raw < 0) {
      usleep(poll_ms * 1000);
      continue;
    }

    /*
     * Spurious raw=0 reads happen when the tsl2772 driver returns
     * before the chip's ADC integration completes (STA_ADC_VALID
     * not yet set - driver returns last cached value, which starts
     * at 0 on probe and apparently can re-zero between samples).
     * Treat raw=0 as "no new sample" and hold smoothed steady,
     * unless we've seen a sustained run of zeros (genuinely dark)
     * - track that with a simple consecutive-zero counter and
     * accept zero as truth after 5 in a row (~1s at 200ms poll).
     */
    static int zero_streak;
    if (raw == 0 && smoothed > 1.0 && zero_streak < zero_streak_limit) {
      zero_streak++;
      usleep(poll_ms * 1000);
      continue;
    }
    zero_streak = 0;

    if (smoothed < 0)
      smoothed = (double)raw;
    else
      smoothed = ema_alpha * raw + (1.0 - ema_alpha) * smoothed;

    double ratio = log10(1.0 + smoothed) / log_max;
    if (ratio > 1.0)
      ratio = 1.0;
    if (ratio < 0.0)
      ratio = 0.0;

    int bri =
        (int)(min_brightness + (max_brightness - min_brightness) * ratio + 0.5);
    if (bri < min_brightness)
      bri = min_brightness;
    if (bri > max_brightness)
      bri = max_brightness;

    if (last_brightness < 0 || abs(bri - last_brightness) >= hysteresis) {
      if (write_int(BL_PATH, bri) == 0)
        last_brightness = bri;
    }

    usleep(poll_ms * 1000);
  }

  return 0;
}
