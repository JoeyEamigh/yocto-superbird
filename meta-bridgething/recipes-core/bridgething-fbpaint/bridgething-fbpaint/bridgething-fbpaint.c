/* Display handoff smoke test for Spotify Superbird.
 *
 * u-boot brings the panel up and leaves the OSD plane scanning out of
 * physical 0x1f800000 (verified via printenv: fb_addr=0x1f800000,
 * fb_width=480, fb_height=800). With the skip-if-bootloader-up patches
 * in the kernel, that pipeline survives the kernel boot intact.
 *
 * This program mmaps /dev/mem at 0x1f800000 and overwrites u-boot's
 * splash with a selectable test pattern. If the pattern appears on
 * the panel, the kernel handoff worked end to end.
 *
 * Pixel format: RGB565 little-endian, 2 bytes/pixel. The OSD plane
 * canvas was configured by u-boot for stride=960 bytes (480 * 2),
 * confirmed via canvas-table read at runtime (DATAL=0x03F00000,
 * DATAH=0x000C800F: stride field = 120 * 8 = 960). This is NOT 32bpp.
 *
 * Usage:  bridgething-fbpaint [pattern]   (default: "hbands")
 *         bridgething-fbpaint --help      (list patterns)
 */

#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#define FB_PHYS  0x1f800000UL
#define WIDTH    480
#define HEIGHT   800
#define BPP      2
#define FB_BYTES ((size_t)WIDTH * HEIGHT * BPP)

#define RGB565(r, g, b) (uint16_t)(((r) >> 3 << 11) | ((g) >> 2 << 5) | ((b) >> 3))

#define RED    RGB565(255,   0,   0)
#define GREEN  RGB565(  0, 255,   0)
#define BLUE   RGB565(  0,   0, 255)
#define WHITE  RGB565(255, 255, 255)
#define BLACK  RGB565(  0,   0,   0)

static void fill_rect(uint16_t *fb, size_t x0, size_t y0,
		      size_t x1, size_t y1, uint16_t c)
{
	for (size_t y = y0; y < y1; y++)
		for (size_t x = x0; x < x1; x++)
			fb[y * WIDTH + x] = c;
}

static void paint_hbands(uint16_t *fb)
{
	static const uint16_t bands[] = { RED, GREEN, BLUE, WHITE };
	const size_t n = sizeof(bands) / sizeof(bands[0]);
	const size_t bh = HEIGHT / n;
	for (size_t i = 0; i < n; i++)
		fill_rect(fb, 0, i * bh, WIDTH, (i + 1) * bh, bands[i]);
	if (n * bh < HEIGHT)
		fill_rect(fb, 0, n * bh, WIDTH, HEIGHT, WHITE);
}

static void paint_vbars(uint16_t *fb)
{
	static const uint16_t bars[] = { RED, GREEN, BLUE, WHITE };
	const size_t n = sizeof(bars) / sizeof(bars[0]);
	const size_t bw = WIDTH / n;
	for (size_t i = 0; i < n; i++)
		fill_rect(fb, i * bw, 0, (i + 1) * bw, HEIGHT, bars[i]);
}

static void paint_vbars_inv(uint16_t *fb)
{
	static const uint16_t bars[] = { WHITE, BLUE, GREEN, RED };
	const size_t n = sizeof(bars) / sizeof(bars[0]);
	const size_t bw = WIDTH / n;
	for (size_t i = 0; i < n; i++)
		fill_rect(fb, i * bw, 0, (i + 1) * bw, HEIGHT, bars[i]);
}

static void paint_corners(uint16_t *fb)
{
	fill_rect(fb, 0, 0, WIDTH, HEIGHT, BLACK);
	fill_rect(fb, 0, 0, 32, 32, RED);
	fill_rect(fb, WIDTH - 32, 0, WIDTH, 32, GREEN);
	fill_rect(fb, 0, HEIGHT - 32, 32, HEIGHT, BLUE);
	fill_rect(fb, WIDTH - 32, HEIGHT - 32, WIDTH, HEIGHT, WHITE);
}

/* Under the wrap model screen[x<30, y] = FB[x+450, y-Δ] (mod 800):
 *   - 5-col red at FB cols 475..479 wraps to screen cols (wrap-5)..(wrap-1).
 *     Position of wrapped red on the left ⇒ exact wrap_width.
 *   - 5-row green at FB rows 795..799 wraps to screen rows
 *     (Δ+795..Δ+799) mod 800 in the leftmost wrap_width cols.
 *     Position of wrapped green near the top ⇒ exact Δ.
 */
static void paint_markers(uint16_t *fb)
{
	fill_rect(fb, 0, 0, WIDTH, HEIGHT, BLACK);
	fill_rect(fb, WIDTH - 5, 0, WIDTH, HEIGHT, RED);
	fill_rect(fb, 0, HEIGHT - 5, WIDTH, HEIGHT, GREEN);
}

static void paint_solid(uint16_t *fb)
{
	fill_rect(fb, 0, 0, WIDTH, HEIGHT, WHITE);
}

typedef struct {
	const char *name;
	const char *summary;
	void (*paint)(uint16_t *fb);
} pattern_t;

static const pattern_t patterns[] = {
	{ "hbands",    "R/G/B/W horizontal bands top-to-bottom (default; boot smoke + Δ-row shift)", paint_hbands },
	{ "vbars",     "R/G/B/W vertical bars left-to-right (exposes ~30-col horizontal wrap)",      paint_vbars },
	{ "vbars-inv", "W/B/G/R vertical bars (symmetry: wrap should bring rightmost band to left)", paint_vbars_inv },
	{ "corners",   "32x32 R/G/B/W squares at each corner on black (orientation / mirror)",       paint_corners },
	{ "markers",   "5-col red right + 5-row green bottom (measure wrap-width and Δ numerically)", paint_markers },
	{ "solid",     "uniform white (null hypothesis: uniform content should show no wrap)",        paint_solid },
};

#define NPATTERNS (sizeof(patterns) / sizeof(patterns[0]))

static void list_patterns(FILE *f)
{
	fprintf(f, "Patterns (pass as argv[1]):\n");
	for (size_t i = 0; i < NPATTERNS; i++)
		fprintf(f, "  %-12s %s\n", patterns[i].name, patterns[i].summary);
}

int main(int argc, char **argv)
{
	const char *name = (argc > 1) ? argv[1] : "hbands";

	if (!strcmp(name, "-h") || !strcmp(name, "--help")) {
		list_patterns(stdout);
		return 0;
	}

	const pattern_t *p = NULL;
	for (size_t i = 0; i < NPATTERNS; i++) {
		if (!strcmp(name, patterns[i].name)) {
			p = &patterns[i];
			break;
		}
	}
	if (!p) {
		fprintf(stderr, "unknown pattern: %s\n", name);
		list_patterns(stderr);
		return 2;
	}

	int fd = open("/dev/mem", O_RDWR | O_SYNC);
	if (fd < 0) {
		perror("open /dev/mem");
		return 1;
	}

	long page = sysconf(_SC_PAGESIZE);
	size_t map_len = (FB_BYTES + page - 1) & ~(size_t)(page - 1);

	void *map = mmap(NULL, map_len, PROT_READ | PROT_WRITE, MAP_SHARED,
			 fd, FB_PHYS);
	if (map == MAP_FAILED) {
		perror("mmap");
		close(fd);
		return 1;
	}

	p->paint((uint16_t *)map);
	msync(map, FB_BYTES, MS_SYNC);
	munmap(map, map_len);
	close(fd);

	printf("painted '%s' (%dx%d RGB565) at phys 0x%lx\n",
	       p->name, WIDTH, HEIGHT, FB_PHYS);
	return 0;
}
