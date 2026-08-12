# Point East Electrical

An Android career game about running one tired diesel generator, and then
seven more.

The world ended, more or less, and then carried on. Cities that still stand run
their own grids because there is nothing left to tie them to. **Dry Green City**
is one of them: seven privately owned generating stations, no intertie, and
whatever the stations make is what the city gets.

You are an electrical engineer four years out of school in New Denport. You took
a two-year contract out here putting other people's work right, and this morning
it ended. In the bar nearest the depot you overhear a man in a twice-mended coat
pitching two investors on an eighth station — out in **Sector E**, which
everybody calls **Point East**.

The dirt out there is dry and dead, so developers will not build. Because nobody
built, nobody ran decent copper. Because the copper is thin, the sector browns
out three or four times a month. And because it browns out, developers will not
build.

The investors leave without putting a number on the table. You go over anyway.
His name is Cal Renner, and as of eleven days ago he is Point East Electrical,
which currently consists of Cal Renner.

He funds one thing: a heavily used 50 kW diesel a friend of yours has sitting on
a pallet. He finds the pad, the switchgear, the copper and the permits. You find
the iron.

A megawatt is one thousand kilowatts. You have fifty.

---

## The grid

Dry Green City runs at **90 Hz**. Nobody alive remembers deciding that. It means
a 6-pole machine turns 1800 rpm:

```
f = rpm × poles / 120   →   1800 × 6 / 120 = 90 Hz
```

Generation is **480 V three-phase** on the plant bus. The station step-up bank
takes it to the **7200 V three-phase primary**, and pole transformers drop that
to a 360 V winding with a grounded centre tap — giving the customer **180-0-180
split phase**: 180 V for lights and outlets, 360 V for ranges, pumps and shop
motors. Sag your bus and every service in the sector sags with it.

The other seven stations are Kessler Light & Power, Ardent Generating,
Tannhauser Bros., Redland Power Co., Verrick Electric, Sixth Street Plant and
Ostrow & Sons. None of them is interested in Sector E.

---

## The one mechanic that matters

**Point East grows because of you, and only because of you.**

Every hour the sector gets clean power *from your plant* — you carrying at least
its load, frequency in band, volts up — somebody decides the risk is worth
taking. A shop opens. A well gets a pump. A block gets wired. Point East's demand
climbs from 46 kW toward 620 kW, and all of that new load is yours to sell.

Power arriving from the other seven keeps the lights on but persuades nobody;
Sector E has been promised things before. And Point East hangs off the end of the
worst feeder in the city, so when the grid runs short it goes dark first — an
hour shed costs about twenty-six hours of earned confidence.

That circle is the whole business plan, and it is the only load in the game that
answers to what you personally do.

---

## How it is simulated

The point of the game is that the machine behaves like a machine.

**The engine is modelled through its air path.** Volumetric efficiency, manifold
pressure and charge temperature set air mass flow. Air mass flow sets how much
fuel can burn cleanly. Fuel burnt gives indicated power; indicated minus friction
(FMEP, rising with speed and falling with oil temperature) and parasitics gives
brake power. Ask for more fuel than you have air for and you get soot, heat and
very little extra power. This is why the turbocharger is the biggest single
upgrade in the tree, and why large injector nozzles are worthless without it.

**Frequency comes out of one swing equation** over the combined inertia of
everything locked to the bus. Nobody assigns anyone a load: each governor sits on
its droop characteristic and frequency settles where generation meets demand.
Raise your speeder and you take load — that is not a metaphor for a slider, it is
what a speeder does. Set the flyweight governor tighter than about 3% and the
linkage hunts.

**Bus voltage is solved, not assumed.** Machines with a regulator move their
field until terminal volts match a reference that falls with the VARs they carry,
so parallel machines share reactive load instead of fighting:

```
V = 1 − droop × (Q_regulated / Q_capability)
```

Your hand rheostat has no such loop. It injects whatever the field produces,
`Q = V(E − V)/X`, and the regulated machines absorb the difference. Over-excite
and you hog VARs off the other stations; under-excite and you make them carry
yours.

**Heat has to go somewhere.** About 28% of the fuel energy goes into the jacket
and 30% out of the stack; the radiator rejects `UA·ΔT` through a thermostat and a
fan whose draw goes with the cube of speed. That, not the fuel pump, is what caps
your continuous rating.

**Synchronising is the real procedure.** Match volts with the field, bring the
machine in slightly *fast* so you pick up load rather than being motored, and
close as the synchroscope passes twelve. Close out of phase and the model
computes the torque shock and damages bearings, windings and the coupling in
proportion.

### Verified numbers

31 JVM tests pin the model to ranges a real 1800 rpm set sits in. At rated load
the founding machine reports:

| Quantity | Model | Realistic for an old IDI diesel |
|---|---|---|
| Specific fuel consumption | 0.307 kg/kWh | 0.28 – 0.33 |
| Volumetric consumption | 0.371 L/kWh | 0.33 – 0.40 |
| Exhaust gas temperature | 372 °C | 350 – 500 |
| Air/fuel ratio | 30 : 1 | 25 – 35 (lean of the smoke limit) |
| Coolant | 88 °C | 82 – 95 |
| Oil pressure | 2.4 bar | 2 – 4 |
| Fuel rack at rated | 70% | well short of the stop |

A scripted operator plays a 400-day career through the same command surface the
screens use, which is how the wear model's bugs were found.

Run them with `./gradlew :core:test`.

---

## The panel

Everything survived from before, so everything looks like 1962: painted
grey-green steel with visible fasteners, engraved phenolic legend plates, chrome
bezels, and moving-iron meters with cream paper faces, black scales and black
needles behind glass. Nothing glows except the indicator lamps, which are
incandescent behind coloured lenses and genuinely dark when off.

Laid out for a phone in one hand: two large meters carry the readings you take
constantly (frequency and kilowatts), a compact row carries the rest, and the
controls sit below in the order you touch them — start, synchronise, load,
excite. Every control is at least 44dp on its short side.

Five screens: **Panel** (one machine, hands on), **Grid** (the city, Point East's
build-out, the other seven stations, dispatch), **Upgrades** (eight branches),
**Plant** (infrastructure, condition, service, the second-hand machinery market)
and **Office** (milestones, ledger, log).

### Starting sequence

1. Open the fuel valve, press START. A cold engine needs more cranking speed
   before it fires, so watch the battery.
2. Let the coolant come up before loading it.
3. Bring the field up until your volts match the bus.
4. Set the speeder so the synchroscope creeps **clockwise** — slightly fast.
5. Close as the pointer passes twelve.
6. Raise the speeder to take load.
7. To come off: wind the load to near zero, *then* open the breaker, then let it
   idle before shutting down.

---

## The career

Sixteen milestones, ending at the first megawatt:

Shakedown → The Lights Stay On → Journeyman → Uprated → **Ground Broken** →
A Station, Not a Shed → Two Machines → Everything the Block Will Take →
Your Own Bus → Off Their Copper → **Sector E** → Five Hundred →
Baseload Contract → Bulk Pricing → N-1 → **The First Megawatt**

Getting there means uprating Set 1 to the limit of what a 7.6 litre block will
take (about 142 kW), then buying machines, then building a powerhouse, your own
480 V bus, your own step-up bank so Kessler stops taking 11% of everything you
sell, a fuel farm, an engine hall deep enough for medium-speed iron, and finally
the certificate that says you can lose your largest machine and still carry your
load.

Prime movers are the hard part. Nobody has built a new engine in a long time, so
everything on the market is second-hand and arrives under a tarp.

---

## Building

Requires the Android SDK (compileSdk 35) and JDK 17 or newer.

```bash
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew :core:test              # the physics and career suite
./gradlew installDebug            # to a connected device
```

Two modules:

- **`core`** — the whole simulation as plain Kotlin with no Android dependency,
  so it can be tested on the JVM without a device. `Genset.kt` is the engine,
  governor and alternator; `Grid.kt` is the bus and Point East's development;
  `Spec.kt` folds the tech tree into an effective machine; `Story.kt` holds the
  prologue and chapter beats; `Sim.kt` owns the world.
- **`app`** — Jetpack Compose. Instruments are drawn on `Canvas`, not assembled
  from widgets.

Minimum Android 8.0 (API 26).

---

## Notes on the fiction

Dry Green City, New Denport, Point East, Cal Renner, the Halvorsen-Marsh HM-6 and
the Kestrel Electric alternator are invented. The seven rival stations are named
after real engines that really did end up in isolated village plants. The 90 Hz
standard is not something you will find in the field, but everything downstream
of it — 1800 rpm on six poles, the transformer ratios, the motor speeds — follows
from it correctly.
