package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/**
 * One left/right pair inside a student-facing matching game,
 * surfaced as part of {@link StudentMatchingGameResponse#items()}.
 *
 * <p>The front-end shuffles the items locally on each play to keep
 * positional answers from being memorised — {@link #sortOrder} is the
 * canonical authoring order and can be used as a stable tie-break,
 * not as the rendered order.</p>
 *
 * <p>{@link #leftImageUrl} / {@link #rightImageUrl} are optional —
 * when present, the renderer shows the image instead of the text on
 * that side; mix-and-match enables "label this picture" or
 * "match the photo" variants. {@link #pinyin} scaffolds beginners
 * who can read pinyin but not yet recognise the Chinese form.</p>
 *
 * <p>Note: the student response intentionally mirrors the admin
 * counterpart shape ({@code AdminMatchingGameItemResponse}) — there is
 * no "correct match" indicator on the wire because the front-end
 * resolves matches by position (left[i] ↔ right[i] in the canonical
 * sort order) and the server validates submissions out-of-band when
 * the activity is completed.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on {@link #id},
 * {@link #leftText} and {@link #rightText} — they map to NOT NULL
 * schema columns and are required by the rendering and matching
 * paths. {@link #leftImageUrl}, {@link #rightImageUrl}, {@link #pinyin}
 * and {@link #sortOrder} may legitimately be {@code null} when the
 * author left them unset.</p>
 *
 * @param id              database-assigned primary key; never
 *                        {@code null}
 * @param leftText        left-side prompt; never {@code null}
 * @param rightText       right-side prompt; never {@code null}
 * @param leftImageUrl    optional left-side image; nullable
 * @param rightImageUrl   optional right-side image; nullable
 * @param pinyin          optional pinyin scaffold; nullable
 * @param sortOrder       canonical authoring order; nullable when the
 *                        author did not pin a sort position
 */
public record StudentMatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer sortOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * required schema columns ({@code id}, {@code leftText},
     * {@code rightText}) so a malformed item cannot reach the wire
     * format and break the front-end matching logic.
     */
    public StudentMatchingGameItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(leftText, "leftText must not be null");
        Objects.requireNonNull(rightText, "rightText must not be null");
    }
}
