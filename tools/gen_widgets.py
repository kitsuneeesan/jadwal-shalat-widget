"""Generate the widget layouts (one per size bucket).

Run after editing:  python3 tools/gen_widgets.py
Colours: res/values/colors.xml (light) and res/values-night/colors.xml (dark); the launcher
picks the right set from the phone's theme.
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/layout"
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
PRAYERS = [(k, f"@string/{k}") for k in ("subuh", "dzuhur", "ashar", "maghrib", "isya")]


def text(id=None, size=13, color="text", bold=False, medium=False, extra="", value=""):
    attrs = [f'android:id="@+id/{id}"' if id else "",
             "" if "layout_width" in extra else 'android:layout_width="wrap_content"',
             'android:layout_height="wrap_content"',
             f'android:textSize="{size}sp" android:textColor="@color/w_{color}"',
             'android:maxLines="1" android:ellipsize="end" android:includeFontPadding="false"',
             'android:shadowColor="@color/w_shadow" android:shadowDy="1" android:shadowRadius="3"',
             'android:textStyle="bold"' if bold else "",
             'android:fontFamily="sans-serif-medium"' if medium else "",
             f'android:text="{value}"' if value else "", extra]
    return "<TextView " + " ".join(a for a in attrs if a) + " />"


def icon(src, dp, id=None, color="icon", extra=""):
    i = f'android:id="@+id/{id}" ' if id else ""
    return (f'<ImageView {i}android:layout_width="{dp}dp" android:layout_height="{dp}dp" '
            f'android:src="@drawable/{src}" android:tint="@color/w_{color}" {extra}/>')


def ll(children, orientation="vertical", w="match_parent", h="wrap_content", extra=""):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" '
            f'android:orientation="{orientation}" {extra}>\n' + "\n".join(children) + "\n</LinearLayout>")


def spacer():
    return '<FrameLayout android:layout_width="0dp" android:layout_height="0dp" android:layout_weight="1" />'


def divider(margins='android:layout_marginVertical="8dp"'):
    return (f'<ImageView android:layout_width="1dp" android:layout_height="match_parent" {margins} '
            'android:background="@color/w_divider" />')


def progress(top=10):
    return (f'<ProgressBar android:id="@+id/progress" style="@android:style/Widget.ProgressBar.Horizontal" '
            f'android:layout_width="match_parent" android:layout_height="4dp" android:layout_marginTop="{top}dp" '
            'android:max="1000" android:progressDrawable="@drawable/progress_glass" />')


def root(children, orientation="vertical", padding="16dp", extra=""):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<LinearLayout {NS} android:id="@+id/root" '
            'android:layout_width="match_parent" android:layout_height="match_parent" '
            f'android:background="@drawable/bg_glass" android:orientation="{orientation}" '
            f'android:padding="{padding}" {extra}>\n' + "\n".join(children) + "\n</LinearLayout>\n")


def location():
    return ll([icon("ic_pin", 12, color="muted"),
               text("location", 11, "muted", extra='android:layout_marginStart="4dp"')],
              "horizontal", "wrap_content", extra='android:gravity="center_vertical"')


def badge(dp=44, icon_dp=24):
    """Next prayer's icon in a round glass badge."""
    return (f'<FrameLayout android:layout_width="{dp}dp" android:layout_height="{dp}dp" '
            'android:background="@drawable/badge_glass">\n'
            + icon("ic_mosque", icon_dp, "next_icon", "accent", 'android:layout_gravity="center" ') + "\n</FrameLayout>")


def header(compact=False):
    """Next prayer badge + location (+ label when there's room): fills the top of the card / 2x2."""
    if compact:
        return ll([badge(40, 22), spacer(), location()], "horizontal", extra='android:gravity="center_vertical"')
    return ll([badge(),
               ll([text(size=11, color="muted", value="@string/next"),
                   ll([location()], extra='android:layout_marginTop="4dp"')],
                  w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"')],
              "horizontal", extra='android:gravity="center_vertical"')


def list_rows(name_sp=13, time_sp=14, gap=10):
    rows = []
    for key, label in PRAYERS:
        rows.append(ll([icon(f"ic_{key}", 16),
                        text(f"name_{key}", name_sp, "muted", medium=True, value=label,
                             extra=f'android:layout_marginStart="{gap}dp" android:layout_width="0dp" android:layout_weight="1"'),
                        text(f"time_{key}", time_sp, "text", bold=True, value="--:--", extra='android:layout_marginStart="6dp"')],
                       "horizontal", h="0dp",
                       extra=f'android:id="@+id/cell_{key}" android:layout_weight="1" android:gravity="center_vertical" '
                             f'android:paddingHorizontal="{gap}dp" android:background="@drawable/cell_idle"'))
    return rows


def next_block(name_sp, time_sp):
    return [text("next_name", name_sp, "accent", medium=True, value="—"),
            text("next_time", time_sp, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
            text("next_in", 12, "muted", extra='android:layout_marginTop="4dp"')]


def card():  # 3x2, 4x2+: next prayer on the left, full schedule on the right
    left = ll([header(), spacer(), *next_block(14, 38), progress()],
              w="0dp", h="match_parent", extra='android:layout_weight="1"')
    right = ll(list_rows(), w="0dp", h="match_parent", extra='android:layout_weight="1.15"')
    gap = divider('android:layout_marginHorizontal="14dp" android:layout_marginVertical="4dp"')
    return root([left, gap, right], "horizontal")


def small():  # 2x2: next prayer
    return root([header(compact=True), spacer(), *next_block(14, 36), progress()])


def tall():  # 2x3+: next prayer header + schedule list
    head = ll([text("next_name", 14, "accent", medium=True, value="—",
                    extra='android:layout_width="0dp" android:layout_weight="1"'),
               text("next_in", 12, "muted")], "horizontal", extra='android:gravity="center_vertical"')
    return root([location(), ll([head], extra='android:layout_marginTop="10dp"'),
                 text("next_time", 32, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
                 progress(), ll(list_rows(12, 13, 8), h="0dp", extra='android:layout_weight="1" android:layout_marginTop="10dp"')])


def row():  # 3x1, 4x1: schedule only
    cells = []
    for i, (key, label) in enumerate(PRAYERS):
        if i:
            cells.append(divider())
        cells.append(ll([icon(f"ic_{key}", 18),
                         text(f"name_{key}", 11, "muted", medium=True, value=label, extra='android:layout_marginTop="3dp"'),
                         text(f"time_{key}", 15, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"')],
                        w="0dp", h="match_parent",
                        extra=f'android:id="@+id/cell_{key}" android:layout_weight="1" android:gravity="center" '
                              'android:background="@drawable/cell_idle"'))
    return root(cells, "horizontal", "6dp")


def tiny():  # 2x1: next prayer at a glance
    col = ll([text("next_name", 12, "accent", medium=True, value="—"),
              text("next_time", 22, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
              text("next_in", 11, "muted", extra='android:layout_marginTop="2dp"')],
             w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"')
    return root([badge(40, 22), col], "horizontal", "10dp", 'android:gravity="center_vertical"')


for old in RES.glob("w_*.xml"):
    old.unlink()
for kind, fn in dict(card=card, small=small, tall=tall, row=row, tiny=tiny).items():
    (RES / f"w_{kind}.xml").write_text(fn())
print("ok")
