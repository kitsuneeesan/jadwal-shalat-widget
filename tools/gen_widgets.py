"""Generate the widget layouts (one per size bucket).

Run after editing:  python3 tools/gen_widgets.py
Colours: res/values/colors.xml (light) and res/values-night/colors.xml (dark); the launcher
picks the right set from the phone's theme.
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/layout"
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
PRAYERS = [(k, f"@string/{k}") for k in ("subuh", "dzuhur", "ashar", "maghrib", "isya")]


def text(id=None, size=13, color="text", bold=False, medium=False, extra="", value="", shadow="w_shadow"):
    attrs = [f'android:id="@+id/{id}"' if id else "",
             "" if "layout_width" in extra else 'android:layout_width="wrap_content"',
             'android:layout_height="wrap_content"',
             f'android:textSize="{size}sp" android:textColor="@color/w_{color}"',
             'android:maxLines="1" android:ellipsize="end" android:includeFontPadding="false"',
             f'android:shadowColor="@color/{shadow}" android:shadowDy="1" android:shadowRadius="3"',
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


def progress(top=10, id="progress", height=4, extra=""):
    return (f'<ProgressBar android:id="@+id/{id}" style="@android:style/Widget.ProgressBar.Horizontal" '
            f'android:layout_width="match_parent" android:layout_height="{height}dp" android:layout_marginTop="{top}dp" '
            f'android:max="1000" android:progressDrawable="@drawable/progress_glass" {extra}/>')


def root(children, orientation="vertical", padding="16dp", extra="", glass=True):
    bg = 'android:background="@drawable/bg_glass" ' if glass else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<LinearLayout {NS} android:id="@+id/root" '
            'android:layout_width="match_parent" android:layout_height="match_parent" '
            f'{bg}android:orientation="{orientation}" '
            f'android:padding="{padding}" {extra}>\n' + "\n".join(children) + "\n</LinearLayout>\n")


def location():
    return ll([icon("ic_pin", 12, color="muted"),
               text("location", 11, "muted", extra='android:layout_marginStart="4dp"')],
              "horizontal", "wrap_content", extra='android:gravity="center_vertical"')


def badge(dp=44, icon_dp=24, src="ic_mosque", icon_id="focus_icon", id=None, color="accent", extra=""):
    """An icon in a round glass badge (the focused prayer's by default)."""
    i = f'android:id="@+id/{id}" ' if id else ""
    return (f'<FrameLayout {i}android:layout_width="{dp}dp" android:layout_height="{dp}dp" '
            f'android:background="@drawable/badge_glass" {extra}>\n'
            + icon(src, icon_dp, icon_id, color, 'android:layout_gravity="center" ') + "\n</FrameLayout>")


def header(compact=False):
    """Focused prayer's badge + location (+ label when there's room): fills the top of the card / 2x2."""
    if compact:
        return ll([badge(40, 22), spacer(), location()], "horizontal", extra='android:gravity="center_vertical"')
    return ll([badge(),
               ll([text("focus_label", 11, "muted", value="@string/now"),
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


def focus_block(name_sp, time_sp):
    return [text("focus_name", name_sp, "accent", medium=True, value="—"),
            text("focus_time", time_sp, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
            text("focus_in", 12, "muted", extra='android:layout_marginTop="4dp"')]


def card():  # 3x2, 4x2+: next prayer on the left, full schedule on the right
    left = ll([header(), spacer(), *focus_block(14, 38), progress()],
              w="0dp", h="match_parent", extra='android:layout_weight="1"')
    right = ll(list_rows(), w="0dp", h="match_parent", extra='android:layout_weight="1.15"')
    gap = divider('android:layout_marginHorizontal="14dp" android:layout_marginVertical="4dp"')
    return root([left, gap, right], "horizontal")


def small():  # 2x2: the prayer in focus on top, then all five times
    head = ll([text("focus_name", 13, "accent", medium=True, value="—",
                    extra='android:layout_width="0dp" android:layout_weight="1"'),
               text("focus_in", 11, "muted")], "horizontal", extra='android:gravity="center_vertical"')
    return root([head,
                 text("focus_time", 28, "text", bold=True, value="--:--", extra='android:layout_marginTop="1dp"'),
                 progress(6),
                 ll(list_rows(11, 12, 6), h="0dp", extra='android:layout_weight="1" android:layout_marginTop="6dp"')],
                padding="14dp")


def tall():  # 2x3+: next prayer header + schedule list
    head = ll([text("focus_name", 14, "accent", medium=True, value="—",
                    extra='android:layout_width="0dp" android:layout_weight="1"'),
               text("focus_in", 12, "muted")], "horizontal", extra='android:gravity="center_vertical"')
    return root([location(), ll([head], extra='android:layout_marginTop="10dp"'),
                 text("focus_time", 32, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
                 progress(), ll(list_rows(12, 13, 8), h="0dp", extra='android:layout_weight="1" android:layout_marginTop="10dp"')])


def row():  # 3x1, 4x1: schedule only
    cells = []
    for i, (key, label) in enumerate(PRAYERS):
        if i:
            cells.append(divider())
        cells.append(ll([icon(f"ic_{key}", 18),
                         text(f"name_{key}", 11, "muted", medium=True, value=label, extra='android:layout_marginTop="3dp"'),
                         text(f"time_{key}", 15, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
                         progress(5, f"bar_{key}", 3, 'android:layout_marginHorizontal="14dp" android:visibility="gone" ')],
                        w="0dp", h="match_parent",
                        extra=f'android:id="@+id/cell_{key}" android:layout_weight="1" android:gravity="center" '
                              'android:background="@drawable/cell_idle"'))
    return root(cells, "horizontal", "6dp")


def tiny():  # 2x1: next prayer at a glance
    col = ll([text("focus_name", 12, "accent", medium=True, value="—"),
              text("focus_time", 22, "text", bold=True, value="--:--", extra='android:layout_marginTop="2dp"'),
              text("focus_in", 11, "muted", extra='android:layout_marginTop="2dp"')],
             w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"')
    return root([badge(40, 22), col], "horizontal", "10dp", 'android:gravity="center_vertical"')


def paragraph(id, weight, color, min_sp, max_sp, extra=""):
    """Multi-line text that shrinks to fit (long verses)."""
    return (f'<TextView android:id="@+id/{id}" android:layout_width="match_parent" android:layout_height="0dp" '
            f'android:layout_weight="{weight}" android:textColor="@color/w_{color}" android:ellipsize="end" '
            f'android:autoSizeTextType="uniform" android:autoSizeMinTextSize="{min_sp}sp" '
            f'android:autoSizeMaxTextSize="{max_sp}sp" {extra}/>')


def lock():  # Samsung lock screen, 2x1 (~128x57dp): bare icon, time, then "Asr · 1h 12m left"
    under = ll([text("focus_name", 10, "accent", medium=True, value="—"),
                text(size=10, color="muted", value="·", extra='android:layout_marginHorizontal="4dp"'),
                text("focus_in", 10, "muted", extra='android:layout_width="0dp" android:layout_weight="1"')],
               "horizontal", extra='android:layout_marginTop="2dp" android:gravity="center_vertical"')
    col = ll([text("focus_time", 20, "text", bold=True, value="--:--"), under],
             w="0dp", extra='android:layout_weight="1" android:layout_marginStart="8dp"')
    return root([icon("ic_mosque", 22, "focus_icon", "accent"), col], "horizontal", "2dp",
                'android:gravity="center_vertical" android:paddingStart="10dp"')


def chevron(id, src, extra=""):
    """A bare ‹ / › button: just the glyph, with padding for a comfortable tap target."""
    return icon(src, 32, id, "icon", f'android:padding="6dp" {extra}')


# The Quran widgets have no glass panel, so their text carries a soft shadow to stay readable on any wallpaper.
QURAN_SHADOW = 'android:shadowColor="@color/q_shadow" android:shadowDy="1" android:shadowRadius="4" '


def quran():  # verse of the day
    head = ll([icon("ic_quran", 24, None, "icon"),
               ll([text("q_name", 13, "text", medium=True, value="@string/widget_quran", shadow="q_shadow"),
                   text("q_meta", 11, "muted", extra='android:layout_marginTop="3dp"', shadow="q_shadow")],
                  w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"'),
               chevron("q_prev", "ic_chevron_left"),
               chevron("q_next", "ic_chevron_right", 'android:layout_marginEnd="-6dp"')],
              "horizontal", extra='android:gravity="center_vertical"')
    # One page of the verse (lines from w_quran_line and/or w_quran_tr), filled in code. Tapping it shows the
    # next page; the dots underneath say which page this is.
    page = ll([], h="0dp", extra='android:id="@+id/q_body" android:layout_weight="1" android:layout_marginTop="6dp" '
                                 'android:gravity="center_vertical"')
    dots = text("q_dots", 10, "muted", extra='android:layout_gravity="center_horizontal" android:letterSpacing="0.3"',
                shadow="q_shadow")
    # No glass panel: the verse sits straight on the wallpaper, in plain white/grey with a soft shadow.
    return root([head, page, dots], glass=False)


def quran_compact():  # verse of the day, 4x1: ‹ [one page, then "Surah 1:7  ● ○" centred underneath] ›
    middle = ll([ll([], h="0dp", extra='android:id="@+id/q_body" android:layout_weight="1" android:gravity="center"'),
                 text("q_meta", 10, "muted", extra='android:layout_gravity="center_horizontal" android:layout_marginTop="2dp"',
                      shadow="q_shadow")],
                w="0dp", h="match_parent", extra='android:layout_weight="1" android:layout_marginHorizontal="6dp"')
    # The chevrons sit right at the widget's edges.
    return root([chevron("q_prev", "ic_chevron_left"), middle, chevron("q_next", "ic_chevron_right")],
                "horizontal", "2dp", 'android:gravity="center_vertical" android:paddingVertical="10dp"', glass=False)


def quran_line():
    """One line of a verse: Arabic with its transliteration right underneath."""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<LinearLayout {NS} android:layout_width="match_parent" '
            'android:layout_height="wrap_content" android:orientation="vertical" android:paddingTop="4dp">\n'
            '<TextView android:id="@+id/q_ar" android:layout_width="match_parent" android:layout_height="wrap_content" '
            'android:gravity="right" android:textSize="22sp" android:textColor="@color/w_text" '
            f'{QURAN_SHADOW}/>\n'
            '<TextView android:id="@+id/q_lat" android:layout_width="match_parent" android:layout_height="wrap_content" '
            'android:gravity="right" android:textSize="12sp" android:textStyle="italic" '
            f'android:textColor="@color/w_muted" {QURAN_SHADOW}/>\n</LinearLayout>\n')


def quran_item(size, color, extra=""):
    """A block of verse text (the translation)."""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<TextView {NS} android:id="@+id/q_item" '
            'android:layout_width="match_parent" android:layout_height="wrap_content" '
            f'android:textSize="{size}sp" android:textColor="@color/w_{color}" {QURAN_SHADOW}{extra}/>\n')


for old in RES.glob("w_*.xml"):
    old.unlink()
for kind, fn in dict(card=card, small=small, tall=tall, row=row, tiny=tiny, lock=lock, quran=quran,
                     quran_compact=quran_compact).items():
    (RES / f"w_{kind}.xml").write_text(fn())
(RES / "w_quran_line.xml").write_text(quran_line())
(RES / "w_quran_tr.xml").write_text(quran_item(13, "muted", 'android:lineSpacingMultiplier="1.1" '))
print("ok")
