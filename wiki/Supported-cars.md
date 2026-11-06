# Supported cars

HMG-API installs and runs on any Gen5W navigation head unit. What depends on your car
is its **hook map**: where each place HMG-API and mods hook is in your car's exact
software. With a map, everything works. Without one, mods still install and run, but
the parts that change the car's own apps stay off, and the app says which.

| Car | Region | Head unit software | Hook map |
|---|---|---|---|
| Kia EV6 | USA | 260507 | ✅ checked on a car (325 of 325 hook points) |
| Kia EV6 | Canada | 260206 | 325 of 325 hook points found in stock files; waiting for a check on a car |
| Hyundai Ioniq 5 | Canada | 260206 | 325 of 325 hook points found in stock files; waiting for a check on a car |
| Hyundai Ioniq 5 (all trims, incl. SE) | USA | 260507 | 325 of 325 hook points found in stock files, same files as 3 Car Reports; waiting for a check on a car |
| Hyundai Ioniq 5 (2023 on) | USA | 260903 | made on 4 cars by HMG-API's own mapper (325 of 325 hook points); waiting for a check on a car |
| Kia Niro Hybrid (DE PE) | Korea | 260902 | ✅ checked on a car (342 of 343 hook points; the split-screen editor's one isn't there) |
| Hyundai Sonata Hybrid (DN8) | Korea | 260902 | made on a car by HMG-API's own mapper, `engine verify-map` passed there (342 of 343 hook points; the split-screen editor's one isn't there); waiting for wireless CarPlay on the car |

**Your car isn't listed, or says "waiting"?** Install HMG-API and make a
[Car Report](Car-Report): HMG-API maps your car from its own software on the spot,
and the report is everything needed to add it. You can also try that map yourself
(Settings → Hook map → "Map this car from its files").

**Only the newest software for each car and region is supported.** If your head unit
has an older version, update it first (the manufacturer's update site or the dealer).

**Not supported:** head units without navigation ("Display Audio") are a different
system. Most Korean units checked so far had no Wi-Fi hardware. The Korean Niro Hybrid
and Sonata Hybrid above have a Wi-Fi chip (it only shows up once Wi-Fi has been switched
on); wireless CarPlay has worked on the Niro, but the hotspot doesn't start by itself on
Korean units yet. Reports are
welcome.
