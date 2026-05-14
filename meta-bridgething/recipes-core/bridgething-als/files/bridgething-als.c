/*
 * bridgething-als - TMD2772 ambient light to pwm-backlight bridge.
 *
 * The mainline tsl2772 driver leaves the chip at minimum sensitivity
 * by default (1x analog gain, single 2.73 ms ADC cycle, ~1024 max
 * counts), well below useful range for room ambient. This daemon
 * writes integration_time and calibscale at startup to put the chip
 * in a regime where typical room light produces tens of counts and
 * direct daylight produces low thousands.
 *
 * Sampling pipeline: poll the clear-photodiode raw count at 5 Hz,
 * push into an N-slot ring, take the median (rejects the spurious
 * raw=0 reads the driver returns when STA_ADC_VALID is clear plus
 * any single-sample spikes), map via log10 to a target brightness,
 * and ease the actual sysfs write toward that target by a percentage
 * of the remaining distance per tick (with a min step of 1 so we
 * never stall short of target). Below DIM_KNEE counts the target
 * clamps to MIN_BRIGHTNESS, so noise-floor jitter in a dark room
 * does not ride the log curve into visible flicker.
 *
 * Why ch0 (clear) not lux: the in-kernel tsl2772 lux table needs a
 * per-board calibration we do not have; the driver overflows lux on
 * the Superbird cover glass once integration is long enough to see
 * useful counts. The raw clear-photodiode count is what the chip
 * actually measures and is monotonic with incident light, which is
 * all this bridge needs.
 *
 * This daemon is the BSP fallback for images that do not run the
 * bridgething Rust daemon. The Rust AlsManager mirrors this logic
 * exactly. Keep them in sync.
 */

#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define ALS_DIR "/sys/bus/iio/devices/iio:device0"
#define ALS_RAW_PATH ALS_DIR "/in_intensity0_raw"
#define ALS_INTEG_PATH ALS_DIR "/in_intensity0_integration_time"
#define ALS_GAIN_PATH ALS_DIR "/in_intensity0_calibscale"
#define BL_PATH "/sys/class/backlight/backlight/brightness"
#define BL_ACTUAL_PATH "/sys/class/backlight/backlight/actual_brightness"
#define MAX_BR_PATH "/sys/class/backlight/backlight/max_brightness"

#define DEFAULT_MIN_BRIGHTNESS 16
#define DEFAULT_POLL_MS 200
#define DEFAULT_RAW_AT_MAX 1500.0
#define DEFAULT_DIM_KNEE 3
#define DEFAULT_MEDIAN_WINDOW 11
#define DEFAULT_EASE_PCT 0.15
#define DEFAULT_INTEG_S 0.100
#define DEFAULT_GAIN 16

#define MAX_MEDIAN_WINDOW 64

static int min_brightness = DEFAULT_MIN_BRIGHTNESS;
static int poll_ms = DEFAULT_POLL_MS;
static double raw_at_max = DEFAULT_RAW_AT_MAX;
static int dim_knee = DEFAULT_DIM_KNEE;
static int median_window = DEFAULT_MEDIAN_WINDOW;
static double ease_pct = DEFAULT_EASE_PCT;
static double integ_s = DEFAULT_INTEG_S;
static int gain = DEFAULT_GAIN;

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

static int write_double(const char *path, double v) {
  int fd = open(path, O_WRONLY);
  if (fd < 0)
    return -1;
  char buf[32];
  int n = snprintf(buf, sizeof(buf), "%.6f\n", v);
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
  if ((e = getenv("ALS_RAW_AT_MAX")) && atof(e) > 0)
    raw_at_max = atof(e);
  if ((e = getenv("ALS_DIM_KNEE")) && atoi(e) >= 0)
    dim_knee = atoi(e);
  if ((e = getenv("ALS_MEDIAN_WINDOW")) && atoi(e) > 0) {
    median_window = atoi(e);
    if (median_window > MAX_MEDIAN_WINDOW)
      median_window = MAX_MEDIAN_WINDOW;
  }
  if ((e = getenv("ALS_EASE_PCT")) && atof(e) > 0)
    ease_pct = atof(e);
  if ((e = getenv("ALS_INTEGRATION_TIME")) && atof(e) > 0)
    integ_s = atof(e);
  if ((e = getenv("ALS_GAIN")) && atoi(e) > 0)
    gain = atoi(e);
}

static int cmp_int(const void *a, const void *b) {
  int x = *(const int *)a;
  int y = *(const int *)b;
  return (x > y) - (x < y);
}

static int target_for_raw(int raw, int max_brightness) {
  int min_t = min_brightness > max_brightness ? max_brightness : min_brightness;
  if (raw <= dim_knee)
    return min_t;
  double log_max = log10(1.0 + raw_at_max);
  double ratio = log10(1.0 + (double)raw) / log_max;
  if (ratio > 1.0)
    ratio = 1.0;
  if (ratio < 0.0)
    ratio = 0.0;
  double span = (double)(max_brightness - min_t);
  return min_t + (int)(span * ratio + 0.5);
}

static int ease_step(int current, int target) {
  if (current == target)
    return current;
  int diff = target - current;
  int mag = diff < 0 ? -diff : diff;
  int step = (int)((double)mag * ease_pct + 0.5);
  if (step < 1)
    step = 1;
  if (step > mag)
    step = mag;
  return diff > 0 ? current + step : current - step;
}

int main(void) {
  struct sigaction sa = {.sa_handler = sig};
  sigaction(SIGTERM, &sa, NULL);
  sigaction(SIGINT, &sa, NULL);
  sigaction(SIGHUP, &sa, NULL);

  load_env();

  if (write_double(ALS_INTEG_PATH, integ_s) < 0)
    fprintf(stderr, "bridgething-als: warning: failed to set integration_time=%.3fs (errno %d)\n", integ_s, errno);
  if (write_int(ALS_GAIN_PATH, gain) < 0)
    fprintf(stderr, "bridgething-als: warning: failed to set gain=%d (errno %d)\n", gain, errno);

  int max_brightness = read_int(MAX_BR_PATH);
  if (max_brightness <= 0) {
    fprintf(stderr, "bridgething-als: cannot read %s (errno %d)\n", MAX_BR_PATH, errno);
    return 1;
  }
  fprintf(stderr,
          "bridgething-als: max=%d min=%d poll=%dms raw_at_max=%.0f knee=%d "
          "window=%d ease=%.2f gain=%d integ=%.3fs\n",
          max_brightness, min_brightness, poll_ms, raw_at_max, dim_knee,
          median_window, ease_pct, gain, integ_s);

  int ring[MAX_MEDIAN_WINDOW];
  int sorted[MAX_MEDIAN_WINDOW];
  int ring_count = 0;
  int ring_head = 0;
  int current = read_int(BL_ACTUAL_PATH);
  if (current < 0 || current > max_brightness)
    current = max_brightness;

  while (running) {
    int raw = read_int(ALS_RAW_PATH);
    if (raw < 0) {
      usleep(poll_ms * 1000);
      continue;
    }

    ring[ring_head] = raw;
    ring_head = (ring_head + 1) % median_window;
    if (ring_count < median_window)
      ring_count++;

    if (ring_count == median_window) {
      memcpy(sorted, ring, sizeof(int) * median_window);
      qsort(sorted, median_window, sizeof(int), cmp_int);
      int median = sorted[median_window / 2];

      int target = target_for_raw(median, max_brightness);
      int next = ease_step(current, target);
      if (next != current) {
        if (write_int(BL_PATH, next) == 0)
          current = next;
      }
    }

    usleep(poll_ms * 1000);
  }

  return 0;
}
