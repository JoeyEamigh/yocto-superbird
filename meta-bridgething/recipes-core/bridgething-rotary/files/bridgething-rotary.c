/*
 * bridgething-rotary - kernel rotary REL_HWHEEL → uinput virtual mouse scroll.
 *
 * The kernel rotary-encoder driver emits EV_REL with code=REL_HWHEEL on each
 * detent. libinput's classifier rejects single-axis devices with no buttons
 * and no REL_X/REL_Y as "not a pointer," so wayland never routes those
 * events as scroll - chromium and other clients see nothing.
 *
 * This daemon grabs the kernel rotary device exclusively, creates a uinput
 * virtual mouse declaring the full pointer capability set (buttons, X/Y,
 * vertical and horizontal wheel + hi-res), and forwards each kernel
 * REL_HWHEEL event as the legacy REL_HWHEEL plus the modern
 * REL_HWHEEL_HI_RES (120 ticks per detent by convention). libinput
 * classifies the uinput device as a real mouse, weston dispatches it as
 * standard horizontal scroll, and webapps see plain wheel events with no
 * cooperation needed.
 *
 * The uinput device declares X/Y/buttons but never emits them, so the
 * cursor never moves and no click/drag activity is synthesized.
 */

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define DEFAULT_HIRES_PER_DETENT 120
#define DEFAULT_LEGACY_PER_DETENT 1
#define DEFAULT_INVERT 0

static int hires_per_detent = DEFAULT_HIRES_PER_DETENT;
static int legacy_per_detent = DEFAULT_LEGACY_PER_DETENT;
static int invert = DEFAULT_INVERT;
static const char *device_override = NULL;

static volatile sig_atomic_t running = 1;
static void sig(int s) {
  (void)s;
  running = 0;
}

static int test_bit(const unsigned long *bits, int b) {
  return (bits[b / (sizeof(long) * 8)] >> (b % (sizeof(long) * 8))) & 1;
}

/*
 * Scan /dev/input/event* for a device that emits REL_HWHEEL but neither
 * REL_X nor REL_Y - the rotary-encoder driver's signature. Returns a
 * malloc'd path on success; caller frees. Avoids hardcoding event-node
 * numbers, which renumber when probe order shifts.
 */
static char *find_rotary_device(void) {
  DIR *d = opendir("/dev/input");
  if (!d)
    return NULL;
  struct dirent *de;
  char *match = NULL;
  while ((de = readdir(d)) != NULL) {
    if (strncmp(de->d_name, "event", 5) != 0)
      continue;
    char path[sizeof("/dev/input/") + sizeof(de->d_name)];
    snprintf(path, sizeof(path), "/dev/input/%s", de->d_name);
    int fd = open(path, O_RDONLY);
    if (fd < 0)
      continue;

    unsigned long ev_bits[(EV_MAX + 8 * sizeof(long) - 1) /
                          (8 * sizeof(long))] = {0};
    unsigned long rel_bits[(REL_MAX + 8 * sizeof(long) - 1) /
                           (8 * sizeof(long))] = {0};
    if (ioctl(fd, EVIOCGBIT(0, sizeof(ev_bits)), ev_bits) < 0 ||
        ioctl(fd, EVIOCGBIT(EV_REL, sizeof(rel_bits)), rel_bits) < 0) {
      close(fd);
      continue;
    }

    if (test_bit(ev_bits, EV_REL) && test_bit(rel_bits, REL_HWHEEL) &&
        !test_bit(rel_bits, REL_X) && !test_bit(rel_bits, REL_Y)) {
      match = strdup(path);
      close(fd);
      break;
    }
    close(fd);
  }
  closedir(d);
  return match;
}

static int open_rotary(const char *path) {
  int fd = open(path, O_RDONLY);
  if (fd < 0) {
    fprintf(stderr, "bridgething-rotary: open %s: %s\n", path, strerror(errno));
    return -1;
  }
  if (ioctl(fd, EVIOCGRAB, 1) < 0) {
    fprintf(stderr, "bridgething-rotary: EVIOCGRAB %s: %s\n", path,
            strerror(errno));
    close(fd);
    return -1;
  }
  return fd;
}

static int create_uinput_mouse(void) {
  int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
  if (fd < 0) {
    fprintf(stderr, "bridgething-rotary: open /dev/uinput: %s\n",
            strerror(errno));
    return -1;
  }

  ioctl(fd, UI_SET_EVBIT, EV_KEY);
  ioctl(fd, UI_SET_EVBIT, EV_REL);
  ioctl(fd, UI_SET_EVBIT, EV_SYN);

  /*
   * Buttons are declared so libinput's classifier accepts the device
   * as a mouse; they are never emitted. Without at least one BTN_*
   * the device falls back to "unknown" and scroll events get dropped.
   */
  ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
  ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
  ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);

  /*
   * Full relative-axis set declared for the same reason. We only ever
   * emit REL_HWHEEL + REL_HWHEEL_HI_RES; X/Y/WHEEL are present so the
   * device looks like a normal scrolling mouse to libinput.
   */
  ioctl(fd, UI_SET_RELBIT, REL_X);
  ioctl(fd, UI_SET_RELBIT, REL_Y);
  ioctl(fd, UI_SET_RELBIT, REL_WHEEL);
  ioctl(fd, UI_SET_RELBIT, REL_HWHEEL);
  ioctl(fd, UI_SET_RELBIT, REL_WHEEL_HI_RES);
  ioctl(fd, UI_SET_RELBIT, REL_HWHEEL_HI_RES);

  struct uinput_setup setup = {0};
  setup.id.bustype = BUS_VIRTUAL;
  setup.id.vendor = 0x1d6b; /* Linux Foundation */
  setup.id.product = 0x0001;
  setup.id.version = 1;
  snprintf(setup.name, sizeof(setup.name), "bridgething-rotary-scroll");

  if (ioctl(fd, UI_DEV_SETUP, &setup) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
    fprintf(stderr, "bridgething-rotary: UI_DEV_SETUP/CREATE: %s\n",
            strerror(errno));
    close(fd);
    return -1;
  }
  return fd;
}

static int emit(int fd, uint16_t type, uint16_t code, int32_t value) {
  struct input_event ev = {.type = type, .code = code, .value = value};
  return write(fd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev) ? 0 : -1;
}

static void load_env(void) {
  const char *e;
  if ((e = getenv("ROTARY_HIRES_PER_DETENT")) && atoi(e) != 0)
    hires_per_detent = atoi(e);
  if ((e = getenv("ROTARY_LEGACY_PER_DETENT")) && atoi(e) != 0)
    legacy_per_detent = atoi(e);
  if ((e = getenv("ROTARY_INVERT")) && atoi(e) != 0)
    invert = 1;
  if ((e = getenv("ROTARY_DEVICE")) && *e)
    device_override = e;
}

int main(void) {
  struct sigaction sa = {.sa_handler = sig};
  sigaction(SIGTERM, &sa, NULL);
  sigaction(SIGINT, &sa, NULL);
  sigaction(SIGHUP, &sa, NULL);

  load_env();

  char *found = NULL;
  const char *path = device_override;
  if (!path) {
    found = find_rotary_device();
    if (!found) {
      fprintf(stderr, "bridgething-rotary: no rotary device found "
                      "(scanning for REL_HWHEEL with no REL_X/Y)\n");
      return 1;
    }
    path = found;
  }

  fprintf(stderr,
          "bridgething-rotary: device=%s hires_per_detent=%d "
          "legacy_per_detent=%d invert=%d\n",
          path, hires_per_detent, legacy_per_detent, invert);

  int rfd = open_rotary(path);
  if (rfd < 0) {
    free(found);
    return 1;
  }

  int ufd = create_uinput_mouse();
  if (ufd < 0) {
    close(rfd);
    free(found);
    return 1;
  }

  while (running) {
    struct input_event ev;
    ssize_t n = read(rfd, &ev, sizeof(ev));
    if (n != (ssize_t)sizeof(ev)) {
      if (errno == EINTR)
        continue;
      fprintf(stderr, "bridgething-rotary: read: %s\n", strerror(errno));
      break;
    }
    if (ev.type != EV_REL || ev.code != REL_HWHEEL)
      continue;

    int v = invert ? -ev.value : ev.value;
    emit(ufd, EV_REL, REL_HWHEEL, v * legacy_per_detent);
    emit(ufd, EV_REL, REL_HWHEEL_HI_RES, v * hires_per_detent);
    emit(ufd, EV_SYN, SYN_REPORT, 0);
  }

  ioctl(ufd, UI_DEV_DESTROY);
  close(ufd);
  close(rfd);
  free(found);
  return 0;
}
