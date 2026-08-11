# Port Annika Power

An Android career game about running a diesel generator, badly at first.

You own Unit 8: a 50 kW Halvorsen-Marsh HM-6, seven and a half litres of
naturally aspirated cast iron that came out of a cannery ice plant. It has a
flyweight governor with a hand speeder and a rheostat on the exciter. Everything
it does, you do.

The other seven machines in Port Annika belong to the co-op. You sell into their
bus. The career ends when a megawatt of the town's plant is yours.

---

## The system

Port Annika is an isolated coastal town with no intertie — whatever the station
makes is what the town gets. The grid standard is **90 Hz**, inherited from the
original cannery's own plant, so a 6-pole machine turns 1800 rpm:

```
f = rpm × poles / 120   →   1800 × 6 / 120 = 90 Hz
```

Generation is at **480 V three-phase** on the plant bus. The station step-up bank
takes that to the **7200 V three-phase primary** out to the town, and pole-top
transformers drop 7200 V to a 360 V winding with a grounded centre tap — giving
customers **180-0-180 split phase**: 180 V for lighting and receptacles, 360 V
for ranges, pumps and the cannery's motors.

Sag the bus and the whole town's service voltage sags with it. The screens show
all three levels because they are the same number.

---

## How it is simulated

The point of the game is that the machine behaves like a machine, so the model
runs on physics rather than on a power curve.

**The engine is modelled through its air path.** Volumetric efficiency, manifold
pressure and charge temperature set air mass flow. Air mass flow sets how much
fuel can burn cleanly. Fuel burnt gives indicated power; indicated minus friction
(FMEP, which rises with speed and falls with oil temperature) and parasitics (fan,
water pump, exciter) gives brake power. Ask for more fuel than you have air for
and you get soot, heat and very little extra power — exactly as a real engine
does. This is why the turbocharger is the single biggest upgrade in the tree, and
why large injector nozzles are worthless without it.

**Frequency comes out of one swing equation** over the combined inertia of
everything locked to the bus. Nobody assigns anyone a load: each governor sits on
its droop characteristic and the frequency settles where total generation meets
demand. Raise your speeder and you take load; that is not a metaphor for a slider,
it is what the speeder does. Set the mechanical governor tighter than about 3% and
the linkage hunts.

**Bus voltage is solved, not assumed.** Machines with an automatic voltage
regulator move their field until terminal volts match a reference that falls with
the VARs they carry, so parallel machines share reactive load instead of fighting:

```
V = 1 − droop × (Q_regulated / Q_capability)
```

A machine on a hand rheostat has no such loop. It injects whatever its field
produces, `Q = V(E − V)/X`, and the regulated machines pick up the remainder.
Over-excite yours and you hog VARs off the co-op; under-excite it and you make
them carry yours. Both are things an operator on a manual exciter has to manage.

**Heat has to go somewhere.** Roughly 28% of the fuel energy goes into the jacket
and 30% out of the stack. The radiator rejects `UA·ΔT` through a thermostat and a
fan whose draw goes with the cube of speed. That is what actually caps your
continuous rating, which is why the cooling branch of the tech tree is not
optional filler.

**Synchronising is the real procedure.** Match volts with the field, bring the
speed in slightly *fast* so you pick up load rather than being motored, and close
as the synchroscope passes twelve. Close out of phase and the model computes the
torque shock and damages bearings, windings and the coupling in proportion.

### Verified numbers

23 JVM tests in `core/src/test` pin the model to ranges a real 1800 rpm set sits
in. At rated load the stock machine reports:

| Quantity | Model | Realistic for an old IDI diesel |
|---|---|---|
| Specific fuel consumption | 0.307 kg/kWh | 0.28 – 0.33 |
| Volumetric consumption | 0.371 L/kWh | 0.33 – 0.40 |
| Exhaust gas temperature | 372 °C | 350 – 500 |
| Air/fuel ratio | 30 : 1 | 25 – 35 (lean of the smoke limit) |
| Coolant | 88 °C | 82 – 95 |
| Oil pressure | 2.4 bar | 2 – 4 |
| Fuel rack at rated | 70% | well short of the stop |

Run them with `./gradlew :core:test`.

---

## Playing it

Five screens along the bottom.

**Panel** — the control panel for one machine. Gauges, synchroscope, breaker,
speeder, droop, field. This is where the game actually is.

**Grid** — system frequency and voltage, the 24-hour load curve with your share
shaded under it, the co-op's seven units, and the dispatcher's calls.

**Upgrades** — eight branches: air, fuel, cooling, bottom end, alternator,
controls, heat recovery, and the plant itself. Prerequisites cross branches on
purpose. You cannot fuel an engine you have not given air to, you cannot sell
140 kW through a 62 kVA alternator, and boost on the stock head gasket will lift
the head.

**Plant** — infrastructure, machine condition and scheduled service, and the
second-hand machinery market. Nothing new ever comes to Port Annika; everything
on the board is a cannery set, an ex-military standby plant, or a tug's auxiliary
that came off the last barge.

**Office** — the thirteen career milestones, the ledger, and the station log.

### Starting sequence

1. Open the fuel valve, press START. A cold engine needs more cranking speed
   before it fires, so watch the battery.
2. Let the coolant come up before loading it.
3. Bring the field up until your volts match the bus.
4. Set the speeder so the synchroscope creeps **clockwise** — slightly fast.
5. Close as the pointer passes twelve.
6. Raise the speeder to take load. On the bus, the speeder is your kW control.
7. To come off: wind the load down to near zero, *then* open the breaker, then
   let it idle before shutting down.

---

## The career

Thirteen milestones, ending at the first megawatt:

Shakedown → Journeyman → Uprated (75 kW) → A Station, Not a Shed → Two Machines →
Everything the Block Will Take (130 kW) → Your Own Bus → Off Their Copper →
Five Hundred → Baseload Contract → Barge Pricing → N-1 → **The First Megawatt**

Getting there means uprating Unit 8 to the limit of what a 7.6 litre block will
take, then buying machines, then building a powerhouse, your own 480 V bus, your
own step-up bank so the co-op stops taking 11% of everything you sell, a fuel
farm so you buy at barge price, an engine hall deep enough for medium-speed iron,
and finally the N-1 certificate that says you can lose your largest unit and
still carry the town.

---

## Building

Requires the Android SDK (compileSdk 35) and a JDK 17 or newer.

```bash
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew :core:test              # the physics calibration suite
./gradlew installDebug            # to a connected device
```

The project is two modules:

- **`core`** — the whole simulation as plain Kotlin with no Android dependency,
  so it can be tested on the JVM without a device. `Genset.kt` is the engine,
  governor and alternator; `Grid.kt` is the bus; `Spec.kt` folds the tech tree
  into an effective machine; `Sim.kt` owns the world.
- **`app`** — Jetpack Compose. The gauges are drawn on `Canvas` rather than
  assembled from widgets, because a panel meter is a picture of a physical thing
  and should read like one: needle position first, number second.

Minimum Android 8.0 (API 26).

---

## Notes on the fiction

Port Annika, the Halvorsen-Marsh HM-6 and the Kestrel Electric alternator are
invented. The co-op's seven machines are named after real engines that really did
end up in isolated village plants. The 90 Hz standard is not something you will
find in the field, but everything downstream of that choice — 1800 rpm on six
poles, the transformer ratios, the motor speeds — follows from it correctly.
