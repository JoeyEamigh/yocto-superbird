// minimal example daemon. writes a heartbeat under /opt/superbird-kiosk/
// every five seconds and logs to stdout (captured by journald via the
// systemd unit). swap this out for your own daemon when forking; the
// recipe + service shape stay the same.

#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

static volatile sig_atomic_t stop_requested = 0;

static void on_stop(int sig) {
    (void)sig;
    stop_requested = 1;
}

int main(void) {
    setvbuf(stdout, NULL, _IOLBF, 0);
    signal(SIGTERM, on_stop);
    signal(SIGINT, on_stop);

    const char *heartbeat_path = "/opt/superbird-kiosk/.heartbeat";

    printf("kioskd: started, heartbeat -> %s\n", heartbeat_path);

    while (!stop_requested) {
        FILE *f = fopen(heartbeat_path, "w");
        if (f) {
            fprintf(f, "%lld\n", (long long)time(NULL));
            fclose(f);
        } else {
            perror("kioskd: open heartbeat");
        }
        for (int i = 0; i < 5 && !stop_requested; i++) {
            sleep(1);
        }
    }

    printf("kioskd: stopping\n");
    return 0;
}
