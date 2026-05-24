package com.example.aichinese.dto.student.activity;

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
 */
public record StudentMatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer sortOrder) {
}
