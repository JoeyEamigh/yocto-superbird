/*
 * bridgething-cursor-suppress - weston module that prevents the pointer
 * cursor sprite from rendering, regardless of what wl_pointer.set_cursor
 * surface a client provides.
 *
 * The Car Thing's only pointer-class input is the kernel rotary-encoder
 * device, which emits EV_REL/REL_HWHEEL on each detent. libinput exposes
 * it to weston as a pointer; weston dispatches REL_HWHEEL as standard
 * horizontal scroll, which is exactly what chromium webapps want. The
 * unwanted side effect is that any pointer device causes chromium to
 * call wl_pointer.set_cursor with its own cursor surface; weston then
 * assigns that surface to pointer->sprite and renders it. Setting
 * cursor-theme=blank in weston.ini does not help because chromium
 * provides its own cursor bitmap rather than naming a theme cursor.
 *
 * This module hooks every weston_output's frame_signal. After each
 * frame, it walks every seat's pointer and forces the sprite view's
 * alpha to 0. The view stays mapped and in compositor->cursor_layer,
 * so libweston's internal bookkeeping is undisturbed; the renderer
 * just paints zero pixels for it, and the drm-backend's plane
 * assigner refuses to commit a non-1.0 alpha view to a HW cursor
 * plane (which forces fallback to GL composition where alpha=0
 * means "draw nothing"). Net effect: rotary scroll flows through
 * libinput and weston as a real wl_pointer.axis stream, but no cursor
 * pixel ever reaches the screen.
 *
 * Earlier versions of this module called weston_view_unmap() on the
 * sprite. That mutates the scene graph (removes the view from the
 * cursor layer's entry list) and is unsafe to do from a frame_signal
 * listener, which fires during the output paint cycle. Setting alpha
 * is a per-view scalar write, safe to do anytime.
 *
 * frame_signal data quirk: in libweston-15, output->frame_signal is
 * emitted by the renderer with the damage region (pixman_region32_t *)
 * as data, NOT the output - see libweston/pixman-renderer.c:677 and
 * renderer-gl/gl-renderer.c:2870. The output handle has to be
 * recovered through container_of() on the wrapping cs_output, the
 * same pattern libweston/screenshooter.c uses for its frame listener.
 * Treating data as a weston_output * crashes on the first paint when
 * the offset-104 deref ([damage+104]) loads garbage and then tries
 * to walk it as compositor->seat_list.
 *
 * VNC-aware bypass: weston's vnc-backend creates a per-client seat
 * named "VNC Client" (libweston/backend-vnc/vnc.c:767). When at
 * least one such seat exists, suppression is skipped for the frame -
 * the cursor stays visible on the panel for that frame too, but
 * remote vnc operators get a real cursor to aim with. Once the VNC
 * client disconnects and its seat is destroyed, normal suppression
 * resumes. The dev image is the only image that loads vnc-backend.so
 * so this branch is dead code on prod.
 *
 * Loaded via [core] modules=bridgething-cursor-suppress.so in
 * weston.ini. Cleanup on compositor destroy walks and frees its
 * per-output listener records.
 */

#include <stdlib.h>
#include <string.h>
#include <wayland-server-core.h>
#include <libweston/libweston.h>
#include <libweston/zalloc.h>
#include <weston/weston.h>

struct cs_output {
	struct wl_list link;
	struct weston_output *output;
	struct wl_listener frame;
	struct wl_listener destroy;
};

struct cs_ctx {
	struct weston_compositor *compositor;
	struct wl_listener output_created;
	struct wl_listener compositor_destroy;
	struct wl_list outputs;
};

static bool
any_vnc_seat_present(struct weston_compositor *compositor)
{
	struct weston_seat *seat;

	wl_list_for_each(seat, &compositor->seat_list, link) {
		if (seat->seat_name &&
		    strcmp(seat->seat_name, "VNC Client") == 0)
			return true;
	}
	return false;
}

static void
hide_pointer_sprites(struct weston_compositor *compositor)
{
	struct weston_seat *seat;

	if (any_vnc_seat_present(compositor))
		return;

	wl_list_for_each(seat, &compositor->seat_list, link) {
		struct weston_pointer *pointer =
			weston_seat_get_pointer(seat);
		if (pointer && pointer->sprite)
			weston_view_set_alpha(pointer->sprite, 0.0f);
	}
}

static void
on_output_frame(struct wl_listener *listener, void *data)
{
	struct cs_output *co = wl_container_of(listener, co, frame);
	hide_pointer_sprites(co->output->compositor);
}

static void
on_output_destroy(struct wl_listener *listener, void *data)
{
	struct cs_output *co = wl_container_of(listener, co, destroy);
	wl_list_remove(&co->frame.link);
	wl_list_remove(&co->destroy.link);
	wl_list_remove(&co->link);
	free(co);
}

static void
attach_output(struct cs_ctx *ctx, struct weston_output *output)
{
	struct cs_output *co = zalloc(sizeof(*co));
	if (!co)
		return;

	co->output = output;
	co->frame.notify = on_output_frame;
	co->destroy.notify = on_output_destroy;
	wl_signal_add(&output->frame_signal, &co->frame);
	wl_signal_add(&output->user_destroy_signal, &co->destroy);
	wl_list_insert(&ctx->outputs, &co->link);
}

static void
on_output_created(struct wl_listener *listener, void *data)
{
	struct cs_ctx *ctx =
		wl_container_of(listener, ctx, output_created);
	attach_output(ctx, data);
}

static void
on_compositor_destroy(struct wl_listener *listener, void *data)
{
	struct cs_ctx *ctx =
		wl_container_of(listener, ctx, compositor_destroy);
	struct cs_output *co, *tmp;

	wl_list_for_each_safe(co, tmp, &ctx->outputs, link) {
		wl_list_remove(&co->frame.link);
		wl_list_remove(&co->destroy.link);
		wl_list_remove(&co->link);
		free(co);
	}
	wl_list_remove(&ctx->output_created.link);
	wl_list_remove(&ctx->compositor_destroy.link);
	free(ctx);
}

WL_EXPORT int
wet_module_init(struct weston_compositor *compositor,
		int *argc, char *argv[])
{
	struct cs_ctx *ctx = zalloc(sizeof(*ctx));
	struct weston_output *output;

	if (!ctx)
		return -1;

	ctx->compositor = compositor;
	wl_list_init(&ctx->outputs);

	wl_list_for_each(output, &compositor->output_list, link)
		attach_output(ctx, output);

	ctx->output_created.notify = on_output_created;
	wl_signal_add(&compositor->output_created_signal,
		      &ctx->output_created);

	ctx->compositor_destroy.notify = on_compositor_destroy;
	wl_signal_add(&compositor->destroy_signal,
		      &ctx->compositor_destroy);

	return 0;
}
