# Car Video

An Android Auto app for the 2023 Suzuki S-Cross that plays videos from your phone on the dash screen, parked only.

Every push to `main` builds the APK automatically and publishes it under **Releases**.

## Dash (phone + Android Auto)

1. Install `CarVideo-Dash` on the phone and open **Car Video**. Tap **Allow access**.
2. Turn on Android Auto developer mode: go to Settings → Android Auto, tap **Version** about 10 times, then go to ⋮ → Developer settings and enable **Unknown sources**.
3. In Android Auto settings → Customise launcher, tick **Car Video**.
4. Plug the phone into the car and open Car Video on the dash. Use the list button to pick a video.

Android Auto shows it as a navigation-style app, because that's the only kind that may draw its own picture. Android Auto doesn't tell apps whether the car is in Park or the handbrake is on, so "parked" here means the car has been at 0 km/h for 10 seconds. The car's own speed is used first, and the phone's GPS is the backup. If neither is available, video stays off. You can change the delay with `PARKED_DELAY_MS` in `DrivingGuard.java`.
