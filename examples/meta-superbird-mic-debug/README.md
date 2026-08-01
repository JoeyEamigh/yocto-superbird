# meta-superbird-mic-debug

A recording image. The Car Thing captures its 4-microphone array to a USB drive
and shows the front end's live state on the panel.

Two streams land on the drive per session:

| stream  | format                                                          | rate     |
| ------- | --------------------------------------------------------------- | -------- |
| `raw/`  | 4-channel interleaved s32 LE, 16 kHz, untouched                 | 256 KB/s |
| `beam/` | mono s16 LE, 16 kHz, the beamformer output the wake word scores | 32 KB/s  |

Together that is about 1 GB per hour.

## What you need

- A **powered** USB-C OTG hub. The Car Thing's single port is also its power
  feed, so the hub has to back-feed it while the device acts as host.
- A USB drive formatted **ext4**. Nothing else is accepted; the journal is what
  makes an abrupt power cut survivable, and FAT does not have one. `mke2fs` and
  `e2fsck` ship in the image if you want to format on the device.

## Build

```sh
cp kas/mic-debug-local.example.yml kas/mic-debug-local.yml   # edit BRIDGETHING_LOCAL
just build mic-debug-local
just flash <image>
```

`kas/mic-debug.yml` on its own builds the recorder from the tracked branch of
the bridgething repo; `mic-debug-local` builds it from a working tree.

## Running a session

The recorder starts at boot, puts the USB port in host role, waits for a drive,
and starts recording into `mic-debug/session-NNNN/` on it. Everything it is
doing is on the screen, including every reason it might not be recording.

Buttons, so nothing needs a touch at speed:

| button                | does                                                             |
| --------------------- | ---------------------------------------------------------------- |
| preset 1, wheel click | mark: about to say the wake word                                 |
| preset 2              | mark: it fired and should not have                               |
| preset 3              | mark: it was said and nothing fired                              |
| preset 4              | cycle the condition tag (highway, windows down, music loud, ...) |
| back, held 2s         | end the session and unmount                                      |

A mark is fsynced the moment it is pressed, because it is the anchor every
recall number is computed against.

## Getting the port back into gadget mode

Host role and the USB network are mutually exclusive, so while recording there
is no SSH over USB. Two ways back:

- Tap the USB chip on the screen and pick device mode. This stops recording.
- `superbird-usb-role persist device` over UART, then reboot. The recorder
  honours a persisted `device` boot role and stays out of host mode.

UART works in either role.

## Reading a session

`meta.json` says how to interpret the bytes. Segments are fixed length and
preallocated, so segment `n` of either stream starts at sample
`n * framesPerSegment` and no index is needed to seek.

`journal.jsonl` is one JSON object per line, each stamped with `rawFrame` and
`beamSample` — the offsets into the two streams — plus a monotonic clock and a
wall clock that is `null` when the device never had one. It carries marks, wake
word scores and detections, beamformer bearing and adoption, tag changes,
overruns, and a `progress` record every second.

That last one is the recovery key: a preallocated segment reads as a full-length
file whether or not it was filled, so **audio past the last `progress` offset is
a power cut, not silence.**
