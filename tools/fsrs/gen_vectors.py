"""Generate FSRS reference vectors from py-fsrs for the Kotlin port's tests.

    python3 -m venv /tmp/fsrs && /tmp/fsrs/bin/pip install fsrs==6.3.2
    /tmp/fsrs/bin/python tools/fsrs/gen_vectors.py > core/src/test/resources/fsrs_vectors.tsv
"""
import json, random, sys
from datetime import datetime, timedelta, timezone
from fsrs import Scheduler, Card, Rating, State

rng = random.Random(20261011)
sched = Scheduler(enable_fuzzing=False)
cases = []
for c in range(60):
    card = Card(card_id=c + 1)
    t = datetime(2026, 1, 1, 9, 0, tzinfo=timezone.utc) + timedelta(minutes=rng.randrange(0, 600))
    reviews = []
    for i in range(14):
        # Mostly Again/Good like the app, with some Hard/Easy for coverage.
        rating = rng.choices([1, 2, 3, 4], weights=[3, 1, 6, 1])[0]
        before_last = card.last_review
        card, _ = sched.review_card(card, Rating(rating), t)
        interval_s = (card.due - t).total_seconds()
        elapsed = (t - before_last).days if before_last else None
        reviews.append({
            "rating": rating,
            "elapsed_days": elapsed,
            "state": int(card.state),
            "step": card.step,
            "stability": card.stability,
            "difficulty": card.difficulty,
            "interval_seconds": interval_s,
        })
        # Next review: on time, early, or late.
        due = card.due
        mode = rng.random()
        if interval_s < 86400:
            t = due + timedelta(minutes=rng.randrange(0, 30))
        elif mode < 0.6:
            t = due + timedelta(hours=rng.randrange(0, 20))
        elif mode < 0.8:
            t = max(t + timedelta(hours=1), due - timedelta(days=rng.randrange(0, 3)))
        else:
            t = due + timedelta(days=rng.randrange(1, 40))
    cases.append(reviews)
print("# py-fsrs 6.3.2, default parameters, retention 0.9, steps 1m 10m / 10m, no fuzz")
print("# case\trating\telapsed_days\tstate\tstep\tstability\tdifficulty\tinterval_seconds")
for n, reviews in enumerate(cases):
    for r in reviews:
        e = -1 if r["elapsed_days"] is None else r["elapsed_days"]
        st = -1 if r["step"] is None else r["step"]
        print(f"{n}\t{r['rating']}\t{e}\t{r['state']}\t{st}\t{r['stability']!r}\t{r['difficulty']!r}\t{r['interval_seconds']!r}")
