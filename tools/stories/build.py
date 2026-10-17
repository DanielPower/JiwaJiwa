# Writes app/src/main/assets/stories/path.json from the stories in series/ and
# the grammar topics in grammar.py. ./gradlew -PcoreOnly=true :core:stories runs
# this, then writes the index and each story's SUMMARY.md (see AGENTS.md here).
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from grammar import GRAMMAR
from story import load

# Every story, in the order the Stories tab lists them: the main stories, each
# following the one before, then side stories and one-shots. Side stories have
# nothing new to learn, so there's always something new to read once you've had
# enough new words for the day. They're longer than the chapters around them,
# and bring back words you've only met once or twice.
STORIES = [
    "arrival",
    "ramen-night",
    "share-house",
    "midnight-konbini",
    "rush-hour",
    "seconds",
    "kentas-side",
    "day-off",
]

missing = sorted(set(os.listdir(os.path.join(HERE, "series"))) - set(STORIES))
if missing:
    sys.exit(f"series/ has stories that aren't in STORIES: {missing}")

stories = [load(os.path.join(HERE, "series", id)) for id in STORIES]
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "../../app/src/main/assets/stories/path.json")
with open(out, "w") as f:
    json.dump({"series": stories, "grammar": GRAMMAR}, f, ensure_ascii=False, indent=1)
print(sum(len(s["chapters"]) for s in stories), "chapters,", len(GRAMMAR), "grammar topics")
