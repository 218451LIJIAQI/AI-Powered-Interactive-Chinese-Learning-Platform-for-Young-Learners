package com.example.aichinese.config.seed;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * The platform's own Learn path, as the prototype's "Library · Curriculum" sets it out: Level 1 (Beginner 1, HSK 1
 * basics) with Units 1-4 (28 lessons) and the Level 1 checkpoint, Level 2 (Beginner 2, HSK 2 basics) with Units 5-8
 * (23 lessons). Unit 1 teaches the sounds: its lessons list words with wrong pinyin made only of sounds taught so far.
 * The other lessons are small lists of words and sentences. {@link LearnQuestionGenerator} turns all of them into
 * questions from what was taught up to that stop, so the content stays coherent and every answer is right by
 * construction.
 */
final class LearnPathContent {

    static final int LESSON_XP = 10;
    static final int BONUS_XP = 15;
    static final int REVIEW_XP = 15;
    static final int CHECKPOINT_XP = 50;

    private static final int LESSON_MINUTES = 4;

    private LearnPathContent() {
    }

    /**
     * A word with its pinyin and English meaning.
     *
     * @param wrongPinyin for a pronunciation exercise: wrong answers made only of sounds taught so far (else empty)
     * @param note an optional pronunciation note shown as part of the explanation
     */
    record Word(String hanzi, String pinyin, String meaning, List<String> wrongPinyin, String note) {

        boolean isSoundDrill() {
            return !wrongPinyin.isEmpty();
        }
    }

    /**
     * A sentence as word tiles separated by spaces, its closing punctuation ("" for a phrase), pinyin, meaning and an
     * optional grammar note shown as the explanation.
     */
    record Sentence(String tiles, String punctuation, String pinyin, String meaning, String note) {

        /** The tiles as written: no spaces between Chinese words, a space before a Latin name ("我叫 Amira"). */
        String hanzi() {
            return tiles.replaceAll("(?<=\\P{IsLatin}) (?=\\P{IsLatin})", "") + punctuation;
        }
    }

    /** "Tap what you hear": the characters read aloud, then the syllables to tap and the wrong syllables among them. */
    record SoundDrill(String audio, String answer, String extras, String meaning, String note) {
    }

    record Phrase(String hanzi, String pinyin, String meaning) {
    }

    record LessonSpec(
            String title,
            LearnLessonKind kind,
            int minutes,
            int xp,
            List<Word> words,
            List<Sentence> sentences,
            SoundDrill drill) {

        /** A pronunciation stop: every word is drilled by its sound, with nothing else asked. */
        boolean isPronunciation() {
            return !words.isEmpty() && kind != LearnLessonKind.REVIEW && words.stream().allMatch(Word::isSoundDrill);
        }
    }

    record UnitSpec(
            int number,
            int level,
            String title,
            String titleZh,
            int hsk,
            String icon,
            PublishStatus status,
            List<Phrase> phrases,
            List<String> tips,
            List<LessonSpec> lessons) {
    }

    static List<UnitSpec> units() {
        return List.of(unit1(), unit2(), unit3(), unit4(), unit5(), unit6(), unit7(), unit8());
    }

    // ===== Level 1 · Beginner 1 · HSK 1 basics =====

    private static UnitSpec unit1() {
        return new UnitSpec(1, 1, "Pinyin and tones", "拼音", 1, "hand", PublishStatus.PUBLISHED,
                List.of(
                        p("妈", "mā", "First tone · high and level"),
                        p("麻", "má", "Second tone · rising"),
                        p("马", "mǎ", "Third tone · low, then rising"),
                        p("骂", "mà", "Fourth tone · sharp and falling"),
                        p("吗", "ma", "Neutral tone · short and light")),
                List.of(
                        "Two third tones together: the first one rises. 你好 is said ní hǎo.",
                        "zh, ch and sh curl the tongue back. z, c and s keep it flat.",
                        "After j, q and x, the letter u is really ü: qù sounds like “chü”.",
                        "an, en end at the front of the mouth. ang, eng end at the back."),
                List.of(
                        sounds("The four tones · 声调",
                                List.of(sound("妈", "mā", "mum", "mǎ", "mà"), sound("马", "mǎ", "horse", "mā", "má"),
                                        sound("吗", "ma", "question word", "mā", "mà")),
                                new SoundDrill("妈 马 吗", "mā mǎ ma", "má mà", "mum · horse · question word",
                                        "Tone 1 stays high, tone 3 dips low, and the neutral tone is short and light.")),
                        sounds("Simple finals · a o e i u ü",
                                List.of(sound("一", "yī", "one", "wū", "yū"), sound("五", "wǔ", "five", "yǔ", "ě"),
                                        sound("鱼", "yú", "fish", "wú", "yí"), sound("饿", "è", "hungry", "à", "ò")),
                                new SoundDrill("一 五 鱼 饿", "yī wǔ yú è", "wū yǔ", "one · five · fish · hungry",
                                        "On their own, i, u and ü are written yi, wu and yu.")),
                        sounds("Initials 1 · b p m f and more",
                                List.of(sound("爸", "bà", "dad", "pà", "mà"), sound("怕", "pà", "to be afraid", "bà", "fà"),
                                        sound("大", "dà", "big", "tà", "là"), sound("读", "dú", "to read", "tú", "lú"),
                                        sound("喝", "hē", "to drink", "kē", "gē")),
                                new SoundDrill("妈 怕 读 喝", "mā pà dú hē", "bà tū gē", "mum · to be afraid · to read · to drink",
                                        "p has a strong puff of air. b has almost none, like the “p” in “spy”.")),
                        sounds("Initials 2 · zh ch sh and more",
                                List.of(sound("七", "qī", "seven", "jī", "xī"), sound("去", "qù", "to go", "jù", "xù"),
                                        sound("是", "shì", "to be", "sì", "zhì"), sound("吃", "chī", "to eat", "cī", "shī"),
                                        sound("字", "zì", "character, word", "cì", "zhì")),
                                new SoundDrill("七 是 吃 字", "qī shì chī zì", "xī sì", "seven · to be · to eat · character",
                                        "zh, ch and sh curl the tongue back. z, c and s keep it flat.")),
                        soundBonus(List.of(sound("爸爸", "bàba", "dad", "pàba", "bābā"), sound("妈妈", "māma", "mum", "māmǎ", "máma"),
                                sound("哥哥", "gēge", "older brother", "kēke", "gége"), sound("弟弟", "dìdi", "younger brother", "tìti", "dīdi"))),
                        sounds("Compound finals · ai ei ao ou",
                                List.of(sound("爱", "ài", "to love", "èi", "ào"), sound("好", "hǎo", "good", "hǒu", "hǎi"),
                                        sound("谁", "shéi", "who", "shái", "shóu"), sound("学", "xué", "to study", "xié", "xuě"),
                                        sound("我", "wǒ", "I, me", "wǎ", "ǒu")),
                                new SoundDrill("爱 好 学 我", "ài hǎo xué wǒ", "èi hǒu", "to love · good · to study · I",
                                        "Glide from the first vowel to the second: a to i in ài, a to o in hǎo.")),
                        sounds("Nasal finals · an en ang eng",
                                List.of(sound("三", "sān", "three", "sāng", "sēn"), sound("人", "rén", "person", "réng", "rán"),
                                        sound("忙", "máng", "busy", "mán", "méng"), sound("中", "zhōng", "middle", "zhēng", "zhāng"),
                                        sound("很", "hěn", "very", "hěng", "hǎn")),
                                new SoundDrill("三 忙 中 很", "sān máng zhōng hěn", "sāng mán", "three · busy · middle · very",
                                        "an, en end at the front of the mouth. ang, eng end at the back.")),
                        review(1,
                                List.of(new Word("你好", "nǐ hǎo", "hello", List.of("nì hǎo", "nǐ hào"),
                                                "Two third tones together: the first one rises, so 你好 is said ní hǎo."),
                                        new Word("爸爸", "bàba", "dad", List.of("pàba", "bābā"),
                                                "The second syllable is in the neutral tone: short and light.")),
                                List.of())));
    }

    private static UnitSpec unit2() {
        return new UnitSpec(2, 1, "Characters and first words", "汉字", 1, "classes", PublishStatus.PUBLISHED,
                List.of(
                        p("你好", "nǐ hǎo", "hello"),
                        p("谢谢", "xièxie", "thank you"),
                        p("对不起", "duìbuqǐ", "sorry"),
                        p("再见", "zàijiàn", "goodbye"),
                        p("我们", "wǒmen", "we")),
                List.of(
                        "Write strokes from top to bottom and from left to right.",
                        "亻 on the left of a character is about people. 女 is about women: 他 and 她 sound the same.",
                        "一 changes its tone before another word: yí ge, yì bēi."),
                List.of(
                        lesson("Strokes and order · 笔画",
                                List.of(w("一", "yī", "one"), w("十", "shí", "ten"), w("人", "rén", "person"),
                                        w("大", "dà", "big"), w("口", "kǒu", "mouth")),
                                List.of()),
                        lesson("First words · 你好 谢谢 再见",
                                List.of(w("你好", "nǐ hǎo", "hello"), w("谢谢", "xièxie", "thank you"),
                                        w("不客气", "bú kèqi", "you're welcome"), w("对不起", "duìbuqǐ", "sorry"),
                                        w("没关系", "méi guānxi", "it's OK"), w("再见", "zàijiàn", "goodbye"),
                                        w("老师", "lǎoshī", "teacher"), w("学生", "xuésheng", "student")),
                                List.of()),
                        lesson("Pronouns · 我 你 他",
                                List.of(w("我", "wǒ", "I, me"), w("你", "nǐ", "you"), w("他", "tā", "he"), w("她", "tā", "she"),
                                        w("我们", "wǒmen", "we"), w("你们", "nǐmen", "you (more than one)"),
                                        w("他们", "tāmen", "they")),
                                List.of(s("我们 学生", "", "wǒmen xuésheng", "we students",
                                                "们 (men) makes a person word plural: 我们, 你们, 他们."),
                                        s("你们 老师", "", "nǐmen lǎoshī", "your teacher",
                                                "A pronoun before a person word says whose: 你们老师 is your teacher."),
                                        s("他们 老师", "", "tāmen lǎoshī", "their teacher", null))),
                        lesson("Numbers 1–10 · 一 二 三",
                                List.of(w("一", "yī", "one"), w("二", "èr", "two"), w("三", "sān", "three"),
                                        w("四", "sì", "four"), w("五", "wǔ", "five"), w("六", "liù", "six"),
                                        w("七", "qī", "seven"), w("八", "bā", "eight"), w("九", "jiǔ", "nine"),
                                        w("十", "shí", "ten")),
                                List.of()),
                        bonus(List.of(w("加油", "jiāyóu", "you can do it"), w("太好了", "tài hǎo le", "that's great"),
                                        w("没问题", "méi wèntí", "no problem"), w("好的", "hǎo de", "okay"), w("棒", "bàng", "great")),
                                List.of()),
                        review(2,
                                List.of(sound("爸", "bà", "dad", "pà", "mà"), sound("是", "shì", "to be", "sì", "zhì")),
                                List.of())));
    }

    private static UnitSpec unit3() {
        return new UnitSpec(3, 1, "First sentences", "是 不 吗", 1, "map", PublishStatus.PUBLISHED,
                List.of(
                        p("我是学生。", "wǒ shì xuésheng.", "I am a student."),
                        p("我叫 Amira。", "wǒ jiào Amira.", "My name is Amira."),
                        p("我不是老师。", "wǒ bú shì lǎoshī.", "I am not a teacher."),
                        p("你是学生吗？", "nǐ shì xuésheng ma?", "Are you a student?"),
                        p("我想喝茶。", "wǒ xiǎng hē chá.", "I want to drink tea.")),
                List.of(
                        "A Chinese sentence runs subject, verb, object: 我喝水。",
                        "Use 叫 (jiào) with names and 是 (shì) with what you are.",
                        "不 (bù) becomes bú before a fourth tone: 不是 bú shì.",
                        "没有 says you don't have something: 不 is not used with 有."),
                List.of(
                        lesson("Word order · 我 喝 水",
                                List.of(w("喝", "hē", "to drink"), w("水", "shuǐ", "water"), w("吃", "chī", "to eat"),
                                        w("饭", "fàn", "rice, meal"), w("茶", "chá", "tea"), w("看", "kàn", "to look, to read"),
                                        w("书", "shū", "book")),
                                List.of(s("我 喝 水", "。", "Wǒ hē shuǐ.", "I drink water.",
                                                "A Chinese sentence runs subject, verb, object."),
                                        s("你 吃 饭", "。", "Nǐ chī fàn.", "You eat.", null),
                                        s("他 看 书", "。", "Tā kàn shū.", "He reads a book.", null))),
                        lesson("To be, to be called · 是 叫",
                                List.of(w("是", "shì", "to be"), w("叫", "jiào", "to be called"), w("这", "zhè", "this"),
                                        w("名字", "míngzi", "name"), w("中国人", "Zhōngguórén", "Chinese person"),
                                        w("马来西亚人", "Mǎláixīyàrén", "Malaysian person")),
                                List.of(s("我 是 学生", "。", "Wǒ shì xuésheng.", "I am a student.", null),
                                        s("我 叫 Amira", "。", "Wǒ jiào Amira.", "My name is Amira.",
                                                "Use 叫 (jiào) with names, not 是."),
                                        s("这 是 书", "。", "Zhè shì shū.", "This is a book.", null))),
                        lesson("Saying no · 不",
                                List.of(w("不", "bù", "not"), w("不是", "bú shì", "is not"), w("对", "duì", "right, correct"),
                                        w("大学生", "dàxuéshēng", "university student")),
                                List.of(s("我 不是 老师", "。", "Wǒ bú shì lǎoshī.", "I am not a teacher.",
                                                "不 (bù) becomes bú before a fourth tone: 不是 bú shì."),
                                        s("他 不 喝 茶", "。", "Tā bù hē chá.", "He doesn't drink tea.",
                                                "Put 不 (bù) before the verb to say no."),
                                        s("我 是 大学生", "。", "Wǒ shì dàxuéshēng.", "I am a university student.", null))),
                        lesson("Questions · 吗 呢",
                                List.of(w("吗", "ma", "question word"), w("呢", "ne", "and you?"), w("也", "yě", "also")),
                                List.of(s("你 是 学生 吗", "？", "Nǐ shì xuésheng ma?", "Are you a student?",
                                                "吗 (ma) at the end turns a sentence into a yes or no question."),
                                        s("我 是 学生， 你 呢", "？", "Wǒ shì xuésheng, nǐ ne?", "I'm a student, and you?",
                                                "呢 (ne) after a person asks “and you?”."),
                                        s("我 也 是 学生", "。", "Wǒ yě shì xuésheng.", "I'm also a student.",
                                                "也 (yě) goes before the verb."))),
                        lesson("Have and my · 有 没 的",
                                List.of(w("有", "yǒu", "to have"), w("没有", "méiyǒu", "not to have"), w("的", "de", "belonging word"),
                                        w("朋友", "péngyou", "friend"), w("爸爸", "bàba", "dad"), w("妈妈", "māma", "mum"),
                                        w("家", "jiā", "family, home")),
                                List.of(s("我 有 朋友", "。", "Wǒ yǒu péngyou.", "I have friends.", null),
                                        s("这 是 我 的 书", "。", "Zhè shì wǒ de shū.", "This is my book.",
                                                "的 (de) shows belonging: 我的书 is my book."),
                                        s("我 没有 书", "。", "Wǒ méiyǒu shū.", "I don't have a book.",
                                                "没有 says you don't have it: 不 is not used with 有."))),
                        lesson("Want · 想 要",
                                List.of(w("买", "mǎi", "to buy"), w("去", "qù", "to go"), w("咖啡", "kāfēi", "coffee"),
                                        w("米饭", "mǐfàn", "cooked rice"), w("想", "xiǎng", "to want to do"), w("要", "yào", "to want to have")),
                                List.of(s("我 想 喝 茶", "。", "Wǒ xiǎng hē chá.", "I want to drink tea.",
                                                "想 (xiǎng) goes before a verb: what you want to do."),
                                        s("我 要 米饭", "。", "Wǒ yào mǐfàn.", "I'd like rice.",
                                                "要 (yào) goes before a thing: what you want to have."),
                                        s("我 想 买 书", "。", "Wǒ xiǎng mǎi shū.", "I want to buy a book.", null))),
                        bonus(List.of(w("早上好", "zǎoshang hǎo", "good morning"), w("晚上好", "wǎnshang hǎo", "good evening"),
                                        w("请", "qǐng", "please")),
                                List.of(s("老师 好", "！", "Lǎoshī hǎo!", "Hello, teacher!",
                                        "Put 好 after a title to greet someone."))),
                        review(3, List.of(),
                                List.of(s("我 叫 Amira， 是 学生", "。", "Wǒ jiào Amira, shì xuésheng.",
                                        "My name is Amira and I am a student.",
                                        "A short self-introduction: your name, then who you are.")))));
    }

    private static UnitSpec unit4() {
        return new UnitSpec(4, 1, "Questions and counting", "什么 个", 1, "calendar", PublishStatus.PUBLISHED,
                List.of(
                        p("我很忙。", "wǒ hěn máng.", "I'm busy."),
                        p("你叫什么名字？", "nǐ jiào shénme míngzi?", "What's your name?"),
                        p("现在三点。", "xiànzài sān diǎn.", "It's three o'clock now."),
                        p("两杯茶", "liǎng bēi chá", "two cups of tea"),
                        p("这本书多少钱？", "zhè běn shū duōshao qián?", "How much is this book?")),
                List.of(
                        "很 (hěn) links a person or thing to an adjective: 我很忙。",
                        "A question word stays where the answer goes: 你叫什么名字？我叫 Amira。",
                        "Number + measure word + noun: 三本书. Two of something is 两, not 二.",
                        "几 (jǐ) asks about small numbers, 多少 (duōshao) about any number."),
                List.of(
                        lesson("Describing · 很 + adjective",
                                List.of(w("很", "hěn", "very"), w("好", "hǎo", "good"), w("大", "dà", "big"), w("小", "xiǎo", "small"),
                                        w("忙", "máng", "busy"), w("高兴", "gāoxìng", "happy"), w("贵", "guì", "expensive")),
                                List.of(s("我 很 忙", "。", "Wǒ hěn máng.", "I'm busy.",
                                                "很 (hěn) links a person or thing to an adjective."),
                                        s("书 很 贵", "。", "Shū hěn guì.", "Books are expensive.", null),
                                        s("我 很 高兴", "。", "Wǒ hěn gāoxìng.", "I'm happy.", null))),
                        lesson("Question words · 什么 谁 哪儿 在",
                                List.of(w("什么", "shénme", "what"), w("谁", "shéi", "who"), w("哪儿", "nǎr", "where"),
                                        w("在", "zài", "to be at"), w("这儿", "zhèr", "here"), w("学校", "xuéxiào", "school")),
                                List.of(s("你 叫 什么 名字", "？", "Nǐ jiào shénme míngzi?", "What's your name?",
                                                "A question word stays where the answer goes."),
                                        s("你 在 哪儿", "？", "Nǐ zài nǎr?", "Where are you?", null),
                                        s("我 在 学校", "。", "Wǒ zài xuéxiào.", "I'm at school.",
                                                "在 (zài) + place says where someone is."))),
                        lesson("Numbers and time · 11–99",
                                List.of(w("今天", "jīntiān", "today"), w("明天", "míngtiān", "tomorrow"), w("现在", "xiànzài", "now"),
                                        w("星期", "xīngqī", "week"), w("点", "diǎn", "o'clock"), w("号", "hào", "day of the month"),
                                        w("月", "yuè", "month")),
                                List.of(s("今天 星期 一", "。", "Jīntiān xīngqīyī.", "Today is Monday.",
                                                "星期 + number names the day: 星期一 is Monday."),
                                        s("现在 三 点", "。", "Xiànzài sān diǎn.", "It's three o'clock now.", null),
                                        s("明天 是 十二 号", "。", "Míngtiān shì shí'èr hào.", "Tomorrow is the 12th.",
                                                "十二 is ten and two; 二十 is two tens."))),
                        lesson("Measure words · 个 杯 本",
                                List.of(w("个", "ge", "general measure word"), w("杯", "bēi", "cup of"), w("本", "běn", "measure word for books"),
                                        w("两", "liǎng", "two (of something)")),
                                List.of(s("一 个 人", "", "yí ge rén", "one person",
                                                "Number + measure word + noun. 个 (ge) is the most common measure word."),
                                        s("两 杯 茶", "", "liǎng bēi chá", "two cups of tea",
                                                "Two of something is 两 (liǎng), not 二."),
                                        s("三 本 书", "", "sān běn shū", "three books",
                                                "本 (běn) counts books."))),
                        lesson("How many · 几 多少",
                                List.of(w("几", "jǐ", "how many (small numbers)"), w("多少", "duōshao", "how many, how much"),
                                        w("钱", "qián", "money"), w("块", "kuài", "ringgit, dollar")),
                                List.of(s("你 有 几 个 朋友", "？", "Nǐ yǒu jǐ ge péngyou?", "How many friends do you have?",
                                                "几 (jǐ) needs a measure word: 几个."),
                                        s("这 本 书 多少 钱", "？", "Zhè běn shū duōshao qián?", "How much is this book?", null),
                                        s("五 块 钱", "", "wǔ kuài qián", "five ringgit",
                                                "块 (kuài) is the everyday word for ringgit or dollars."))),
                        review(4, List.of(), List.of()),
                        new LessonSpec("Level 1 checkpoint", LearnLessonKind.CHECKPOINT, 10, CHECKPOINT_XP, List.of(), List.of(), null)));
    }

    // ===== Level 2 · Beginner 2 · HSK 2 basics =====

    private static UnitSpec unit5() {
        return new UnitSpec(5, 2, "Time and completed actions", "了 以后", 2, "calendar", PublishStatus.PUBLISHED,
                List.of(
                        p("我昨天去了学校。", "wǒ zuótiān qù le xuéxiào.", "I went to school yesterday."),
                        p("我吃了饭。", "wǒ chī le fàn.", "I ate."),
                        p("下课以后", "xiàkè yǐhòu", "after class"),
                        p("上课以前", "shàngkè yǐqián", "before class"),
                        p("你吃饭了吗？", "nǐ chīfàn le ma?", "Have you eaten?")),
                List.of(
                        "了 (le) after a verb shows a completed action: 我去了学校。",
                        "Time words go before the verb: 我昨天去了学校。",
                        "以前 and 以后 come after the event: 下课以后 is after class."),
                List.of(
                        lesson("Yesterday and today · 昨天",
                                List.of(w("昨天", "zuótiān", "yesterday"), w("上午", "shàngwǔ", "morning"), w("下午", "xiàwǔ", "afternoon"),
                                        w("晚上", "wǎnshang", "evening"), w("早上", "zǎoshang", "early morning"), w("中午", "zhōngwǔ", "noon")),
                                List.of(s("我 昨天 很 忙", "。", "Wǒ zuótiān hěn máng.", "I was busy yesterday.",
                                                "Time words go before the verb or adjective."),
                                        s("今天 下午 我 去 学校", "。", "Jīntiān xiàwǔ wǒ qù xuéxiào.", "I'm going to school this afternoon.", null))),
                        lesson("Completed actions · 了",
                                List.of(w("了", "le", "completed action word"), w("来", "lái", "to come"), w("回", "huí", "to go back"),
                                        w("早饭", "zǎofàn", "breakfast"), w("吃饭", "chīfàn", "to have a meal"), w("看见", "kànjiàn", "to see")),
                                List.of(s("我 昨天 去 了 学校", "。", "Wǒ zuótiān qù le xuéxiào.", "I went to school yesterday.",
                                                "了 (le) after a verb shows a completed action."),
                                        s("他 买 了 三 本 书", "。", "Tā mǎi le sān běn shū.", "He bought three books.", null))),
                        lesson("Before and after · 以前 以后",
                                List.of(w("以前", "yǐqián", "before"), w("以后", "yǐhòu", "after"), w("上课", "shàngkè", "to start class"),
                                        w("下课", "xiàkè", "to finish class"), w("回家", "huí jiā", "to go home"), w("睡觉", "shuìjiào", "to sleep")),
                                List.of(s("下课 以后 我 回家", "。", "Xiàkè yǐhòu wǒ huí jiā.", "I go home after class.",
                                                "以后 (yǐhòu) comes after the event."),
                                        s("上课 以前 我 喝 咖啡", "。", "Shàngkè yǐqián wǒ hē kāfēi.", "I drink coffee before class.", null))),
                        lesson("Have you done it? · 了吗",
                                List.of(w("已经", "yǐjīng", "already"), w("还", "hái", "still"), w("没", "méi", "did not"),
                                        w("作业", "zuòyè", "homework"), w("做", "zuò", "to do"), w("完", "wán", "to finish")),
                                List.of(s("你 吃饭 了 吗", "？", "Nǐ chīfàn le ma?", "Have you eaten?",
                                                "了吗 asks if something is done."),
                                        s("我 已经 做 了 作业", "。", "Wǒ yǐjīng zuò le zuòyè.", "I already did my homework.", null))),
                        bonus(List.of(w("昨天晚上", "zuótiān wǎnshang", "last night"), w("刚才", "gāngcái", "just now"), w("前天", "qiántiān", "the day before yesterday")),
                                List.of(s("我 刚才 吃 了 饭", "。", "Wǒ gāngcái chī le fàn.", "I ate just now.", null))),
                        review(5, List.of(), List.of())));
    }

    private static UnitSpec unit6() {
        return new UnitSpec(6, 2, "Comparing", "比 更 最", 2, "shopping", PublishStatus.PUBLISHED,
                List.of(
                        p("今天比昨天忙。", "jīntiān bǐ zuótiān máng.", "Today is busier than yesterday."),
                        p("这个更好。", "zhège gèng hǎo.", "This one is even better."),
                        p("这个最贵。", "zhège zuì guì.", "This one is the most expensive."),
                        p("我没有他高。", "wǒ méiyǒu tā gāo.", "I'm not as tall as he is."),
                        p("一样大", "yíyàng dà", "the same size")),
                List.of(
                        "A 比 B + adjective: 今天比昨天忙。 Do not add 很 after 比.",
                        "更 (gèng) means even more, 最 (zuì) means the most.",
                        "A 没有 B + adjective: A is not as … as B."),
                List.of(
                        lesson("Comparing two things · 比",
                                List.of(w("比", "bǐ", "compared with"), w("高", "gāo", "tall, high"), w("矮", "ǎi", "short"),
                                        w("多", "duō", "many, much"), w("少", "shǎo", "few, little"), w("这个", "zhège", "this one")),
                                List.of(s("今天 比 昨天 忙", "。", "Jīntiān bǐ zuótiān máng.", "Today is busier than yesterday.",
                                                "A 比 B + adjective compares A with B."),
                                        s("他 比 我 高", "。", "Tā bǐ wǒ gāo.", "He is taller than me.", null))),
                        lesson("Even more · 更",
                                List.of(w("更", "gèng", "even more"), w("便宜", "piányi", "cheap"), w("快", "kuài", "fast"),
                                        w("慢", "màn", "slow"), w("好吃", "hǎochī", "tasty"), w("那个", "nàge", "that one")),
                                List.of(s("这个 更 好", "。", "Zhège gèng hǎo.", "This one is even better.",
                                                "更 (gèng) goes before the adjective."),
                                        s("那个 更 便宜", "。", "Nàge gèng piányi.", "That one is even cheaper.", null))),
                        lesson("The most · 最",
                                List.of(w("最", "zuì", "the most"), w("喜欢", "xǐhuan", "to like"), w("难", "nán", "difficult"),
                                        w("容易", "róngyì", "easy"), w("漂亮", "piàoliang", "beautiful"), w("有名", "yǒumíng", "famous")),
                                List.of(s("这个 最 贵", "。", "Zhège zuì guì.", "This one is the most expensive.",
                                                "最 (zuì) goes before the adjective."),
                                        s("我 最 喜欢 喝 茶", "。", "Wǒ zuì xǐhuan hē chá.", "I like drinking tea the most.", null))),
                        lesson("Not as much as · 没有",
                                List.of(w("一样", "yíyàng", "the same"), w("跟", "gēn", "and, with"), w("远", "yuǎn", "far"),
                                        w("近", "jìn", "near"), w("热", "rè", "hot"), w("冷", "lěng", "cold")),
                                List.of(s("我 没有 他 高", "。", "Wǒ méiyǒu tā gāo.", "I'm not as tall as he is.",
                                                "A 没有 B + adjective: A is not as … as B."),
                                        s("今天 跟 昨天 一样 热", "。", "Jīntiān gēn zuótiān yíyàng rè.", "Today is as hot as yesterday.", null))),
                        bonus(List.of(w("大家", "dàjiā", "everyone"), w("第一", "dì-yī", "first"), w("差不多", "chàbuduō", "about the same")),
                                List.of(s("这 两 个 差不多", "。", "Zhè liǎng ge chàbuduō.", "These two are about the same.", null))),
                        review(6, List.of(), List.of())));
    }

    private static UnitSpec unit7() {
        return new UnitSpec(7, 2, "Actions and experience", "正在 过", 2, "briefcase", PublishStatus.DRAFT,
                List.of(
                        p("我正在吃饭。", "wǒ zhèngzài chīfàn.", "I'm eating."),
                        p("我去过上海。", "wǒ qùguo Shànghǎi.", "I have been to Shanghai."),
                        p("你看过吗？", "nǐ kànguo ma?", "Have you seen it?"),
                        p("他在看书呢。", "tā zài kàn shū ne.", "He is reading."),
                        p("我没去过。", "wǒ méi qùguo.", "I have never been.")),
                List.of(
                        "正在 (zhèngzài) before a verb: the action is happening now.",
                        "过 (guo) after a verb: you have done it before.",
                        "没 + verb + 过 says you have never done it."),
                List.of(
                        lesson("Right now · 正在",
                                List.of(w("正在", "zhèngzài", "in the middle of"), w("上班", "shàngbān", "to be at work"), w("休息", "xiūxi", "to rest"),
                                        w("学习", "xuéxí", "to study"), w("写", "xiě", "to write"), w("听", "tīng", "to listen")),
                                List.of(s("我 正在 吃饭", "。", "Wǒ zhèngzài chīfàn.", "I'm eating.",
                                                "正在 (zhèngzài) before a verb: the action is happening now."),
                                        s("他 正在 学习", "。", "Tā zhèngzài xuéxí.", "He is studying.", null))),
                        lesson("Have you ever · 过",
                                List.of(w("过", "guo", "experience word"), w("上海", "Shànghǎi", "Shanghai"), w("北京", "Běijīng", "Beijing"),
                                        w("电影", "diànyǐng", "film"), w("中国", "Zhōngguó", "China"), w("次", "cì", "time, occasion")),
                                List.of(s("我 去 过 上海", "。", "Wǒ qù guo Shànghǎi.", "I have been to Shanghai.",
                                                "过 (guo) after a verb: you have done it before."),
                                        s("你 看 过 这 个 电影 吗", "？", "Nǐ kàn guo zhège diànyǐng ma?", "Have you seen this film?", null))),
                        lesson("Still doing · 在 呢",
                                List.of(w("开会", "kāihuì", "to have a meeting"), w("打电话", "dǎ diànhuà", "to make a call"), w("等", "děng", "to wait"),
                                        w("唱歌", "chànggē", "to sing"), w("跑步", "pǎobù", "to run"), w("玩", "wán", "to play")),
                                List.of(s("他 在 看 书 呢", "。", "Tā zài kàn shū ne.", "He is reading.",
                                                "在 (zài) before a verb and 呢 (ne) at the end: it is going on."),
                                        s("我 在 等 你 呢", "。", "Wǒ zài děng nǐ ne.", "I'm waiting for you.", null))),
                        lesson("Never done · 没 过",
                                List.of(w("从来", "cónglái", "ever"), w("旅行", "lǚxíng", "to travel"), w("飞机", "fēijī", "plane"),
                                        w("坐", "zuò", "to take (a plane or bus)"), w("见", "jiàn", "to see, to meet"), w("听说", "tīngshuō", "to hear of")),
                                List.of(s("我 没 去 过 北京", "。", "Wǒ méi qù guo Běijīng.", "I have never been to Beijing.",
                                                "没 + verb + 过 says you have never done it."),
                                        s("他 没 坐 过 飞机", "。", "Tā méi zuò guo fēijī.", "He has never been on a plane.", null))),
                        review(7, List.of(), List.of())));
    }

    private static UnitSpec unit8() {
        return new UnitSpec(8, 2, "Abilities and reasons", "会 能 因为", 2, "globe", PublishStatus.DRAFT,
                List.of(
                        p("我会说中文。", "wǒ huì shuō Zhōngwén.", "I can speak Chinese."),
                        p("你能来吗？", "nǐ néng lái ma?", "Can you come?"),
                        p("因为下雨，所以我不去。", "yīnwèi xià yǔ, suǒyǐ wǒ bú qù.", "Because it is raining, I'm not going."),
                        p("我可以看吗？", "wǒ kěyǐ kàn ma?", "May I have a look?"),
                        p("他不会开车。", "tā bú huì kāichē.", "He can't drive.")),
                List.of(
                        "会 (huì): a skill you learned. 能 (néng): you are able to right now.",
                        "可以 (kěyǐ) asks for or gives permission.",
                        "因为 … 所以 … goes from the reason to the result."),
                List.of(
                        lesson("Skills · 会",
                                List.of(w("会", "huì", "can (a learned skill)"), w("说", "shuō", "to speak"), w("中文", "Zhōngwén", "Chinese"),
                                        w("英文", "Yīngwén", "English"), w("开车", "kāichē", "to drive"), w("游泳", "yóuyǒng", "to swim")),
                                List.of(s("我 会 说 中文", "。", "Wǒ huì shuō Zhōngwén.", "I can speak Chinese.",
                                                "会 (huì) is for a skill you learned."),
                                        s("他 不 会 开车", "。", "Tā bú huì kāichē.", "He can't drive.", null))),
                        lesson("Able to · 能",
                                List.of(w("能", "néng", "can (able to now)"), w("帮", "bāng", "to help"), w("晚", "wǎn", "late"),
                                        w("早", "zǎo", "early"), w("时间", "shíjiān", "time"), w("有空", "yǒu kòng", "to be free")),
                                List.of(s("你 能 来 吗", "？", "Nǐ néng lái ma?", "Can you come?",
                                                "能 (néng) is for being able to right now."),
                                        s("我 今天 不 能 去", "。", "Wǒ jīntiān bù néng qù.", "I can't go today.", null))),
                        lesson("Because · 因为 所以",
                                List.of(w("因为", "yīnwèi", "because"), w("所以", "suǒyǐ", "so"), w("下雨", "xià yǔ", "to rain"),
                                        w("累", "lèi", "tired"), w("病", "bìng", "ill"), w("医院", "yīyuàn", "hospital")),
                                List.of(s("因为 下雨， 所以 我 不 去", "。", "Yīnwèi xià yǔ, suǒyǐ wǒ bú qù.", "Because it is raining, I'm not going.",
                                                "因为 … 所以 … goes from the reason to the result."),
                                        s("我 很 累， 所以 回家", "。", "Wǒ hěn lèi, suǒyǐ huí jiā.", "I'm tired, so I'm going home.", null))),
                        lesson("May I · 可以",
                                List.of(w("可以", "kěyǐ", "may, can"), w("请问", "qǐngwèn", "excuse me"), w("进", "jìn", "to come in"),
                                        w("坐下", "zuòxià", "to sit down"), w("问题", "wèntí", "question"), w("手机", "shǒujī", "mobile phone")),
                                List.of(s("我 可以 看 吗", "？", "Wǒ kěyǐ kàn ma?", "May I have a look?",
                                                "可以 (kěyǐ) asks for or gives permission."),
                                        s("请问 我 可以 进 吗", "？", "Qǐngwèn wǒ kěyǐ jìn ma?", "Excuse me, may I come in?", null))),
                        bonus(List.of(w("当然", "dāngrán", "of course"), w("没关系的", "méi guānxi de", "it's fine"), w("真的", "zhēn de", "really")),
                                List.of(s("当然 可以", "！", "Dāngrán kěyǐ!", "Of course you can!", null))),
                        review(8, List.of(), List.of())));
    }

    // ===== Builders =====

    private static Word w(String hanzi, String pinyin, String meaning) {
        return new Word(hanzi, pinyin, meaning, List.of(), null);
    }

    private static Word sound(String hanzi, String pinyin, String meaning, String wrong1, String wrong2) {
        return new Word(hanzi, pinyin, meaning, List.of(wrong1, wrong2), null);
    }

    private static Sentence s(String tiles, String punctuation, String pinyin, String meaning, String note) {
        return new Sentence(tiles, punctuation, pinyin, meaning, note);
    }

    private static Phrase p(String hanzi, String pinyin, String meaning) {
        return new Phrase(hanzi, pinyin, meaning);
    }

    private static LessonSpec lesson(String title, List<Word> words, List<Sentence> sentences) {
        return new LessonSpec(title, LearnLessonKind.LESSON, LESSON_MINUTES, LESSON_XP, words, sentences, null);
    }

    private static LessonSpec sounds(String title, List<Word> words, SoundDrill drill) {
        return new LessonSpec(title, LearnLessonKind.LESSON, LESSON_MINUTES, LESSON_XP, words, List.of(), drill);
    }

    private static LessonSpec bonus(List<Word> words, List<Sentence> sentences) {
        return new LessonSpec("Bonus chest", LearnLessonKind.BONUS, 3, BONUS_XP, words, sentences, null);
    }

    private static LessonSpec soundBonus(List<Word> words) {
        return bonus(words, List.of());
    }

    /** A unit review: {@code words} and {@code sentences} are what it adds to the unit's lessons (often nothing). */
    private static LessonSpec review(int unit, List<Word> words, List<Sentence> sentences) {
        return new LessonSpec("Unit " + unit + " review", LearnLessonKind.REVIEW, 6, REVIEW_XP, words, sentences, null);
    }
}
