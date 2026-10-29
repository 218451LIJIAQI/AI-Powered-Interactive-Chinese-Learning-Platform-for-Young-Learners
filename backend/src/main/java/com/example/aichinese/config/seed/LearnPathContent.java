package com.example.aichinese.config.seed;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * The platform's own Learn path: Level 1 (Beginner 1, HSK 1) with Units 1-4 and the Level 1 checkpoint, Level 2
 * (Beginner 2, HSK 2) with Units 5-8. Each lesson is a small list of words and sentences; {@link LearnQuestionGenerator}
 * turns them into questions, so the content stays coherent and every answer is right by construction.
 */
final class LearnPathContent {

    static final int LESSON_XP = 10;
    static final int BONUS_XP = 15;
    static final int REVIEW_XP = 15;
    static final int CHECKPOINT_XP = 50;

    private LearnPathContent() {
    }

    /** A word with its pinyin and English meaning. */
    record Word(String hanzi, String pinyin, String meaning) {
    }

    /**
     * A sentence as word tiles separated by spaces, its closing punctuation ("" for a phrase), pinyin, meaning and an
     * optional grammar note shown as the explanation.
     */
    record Sentence(String tiles, String punctuation, String pinyin, String meaning, String note) {

        String hanzi() {
            return tiles.replace(" ", "") + punctuation;
        }
    }

    record Phrase(String hanzi, String pinyin, String meaning) {
    }

    record LessonSpec(String title, LearnLessonKind kind, int minutes, int xp, List<Word> words, List<Sentence> sentences) {
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

    // ===== Level 1 · Beginner 1 · HSK 1 =====

    private static UnitSpec unit1() {
        return new UnitSpec(1, 1, "Say hello", "你好", 1, "hand", PublishStatus.PUBLISHED,
                List.of(
                        p("你好", "nǐ hǎo", "hello"),
                        p("再见", "zàijiàn", "goodbye"),
                        p("谢谢", "xièxie", "thank you"),
                        p("我叫……", "wǒ jiào…", "my name is…"),
                        p("你叫什么名字？", "nǐ jiào shénme míngzi?", "what's your name?")),
                List.of(
                        "好 (hǎo) uses the third tone: low, then rising.",
                        "Use 叫 (jiào) to give a name: 我叫小美。",
                        "你好 works any time of day. 早上好 is only for the morning."),
                List.of(
                        lesson("Hello and goodbye", 4,
                                List.of(w("你好", "nǐ hǎo", "hello"), w("再见", "zàijiàn", "goodbye"),
                                        w("早上好", "zǎoshang hǎo", "good morning"),
                                        w("晚上好", "wǎnshang hǎo", "good evening")),
                                List.of(s("老师 好", "！", "Lǎoshī hǎo!", "Hello, teacher!",
                                                "Put 好 after a title to greet someone: 老师好！"),
                                        s("明天 见", "！", "Míngtiān jiàn!", "See you tomorrow!", null))),
                        lesson("Polite phrases", 4,
                                List.of(w("谢谢", "xièxie", "thank you"), w("不客气", "bú kèqi", "you're welcome"),
                                        w("对不起", "duìbuqǐ", "sorry"), w("没关系", "méi guānxi", "it's OK"),
                                        w("请", "qǐng", "please")),
                                List.of(s("谢谢 你", "。", "Xièxie nǐ.", "Thank you.", null),
                                        s("请 进", "。", "Qǐng jìn.", "Please come in.", "请 (qǐng) before a verb makes a polite request."))),
                        lesson("Introduce yourself", 4,
                                List.of(w("我", "wǒ", "I, me"), w("叫", "jiào", "to be called"),
                                        w("名字", "míngzi", "name"), w("什么", "shénme", "what"),
                                        w("学生", "xuésheng", "student")),
                                List.of(s("我 叫 小美", "。", "Wǒ jiào Xiǎoměi.", "My name is Xiaomei.",
                                                "Use 叫 (jiào) to give your name: 我叫……"),
                                        s("你 叫 什么 名字", "？", "Nǐ jiào shénme míngzi?", "What's your name?",
                                                "Use 叫 (to be called) with names, not 是."),
                                        s("我 是 学生", "。", "Wǒ shì xuésheng.", "I am a student.", null))),
                        lesson("Numbers 1–10", 5,
                                List.of(w("一", "yī", "one"), w("二", "èr", "two"), w("三", "sān", "three"),
                                        w("五", "wǔ", "five"), w("八", "bā", "eight"), w("十", "shí", "ten")),
                                List.of(s("我 十八 岁", "。", "Wǒ shíbā suì.", "I am eighteen.",
                                                "岁 (suì) gives your age: 我十八岁。"),
                                        s("三 个 学生", "", "sān ge xuésheng", "three students",
                                                "Put 个 (ge) between a number and a noun."))),
                        bonus(List.of(w("加油", "jiāyóu", "you can do it"), w("太好了", "tài hǎo le", "that's great"),
                                        w("没问题", "méi wèntí", "no problem"), w("好的", "hǎo de", "okay")),
                                List.of(s("你 太 棒 了", "！", "Nǐ tài bàng le!", "You're awesome!",
                                        "棒 (bàng) means great. You see it when you finish a lesson."))),
                        lesson("Family", 4,
                                List.of(w("爸爸", "bàba", "dad"), w("妈妈", "māma", "mum"),
                                        w("哥哥", "gēge", "older brother"), w("妹妹", "mèimei", "younger sister"),
                                        w("家", "jiā", "family, home")),
                                List.of(s("这 是 我 妈妈", "。", "Zhè shì wǒ māma.", "This is my mum.", null),
                                        s("我 家 有 四 口 人", "。", "Wǒ jiā yǒu sì kǒu rén.",
                                                "There are four people in my family.",
                                                "口 (kǒu) counts the people in a family."))),
                        review(1)));
    }

    private static UnitSpec unit2() {
        return new UnitSpec(2, 1, "Food and drink", "吃喝", 1, "utensils", PublishStatus.PUBLISHED,
                List.of(
                        p("我要……", "wǒ yào…", "I'd like…"),
                        p("多少钱？", "duōshao qián?", "how much is it?"),
                        p("一杯茶", "yì bēi chá", "a cup of tea"),
                        p("我不吃辣。", "wǒ bù chī là.", "I don't eat spicy food."),
                        p("好吃！", "hǎochī!", "tasty!")),
                List.of(
                        "我要…… (wǒ yào…) is the easiest way to order.",
                        "Measure words go between a number and a noun: 一杯茶, 一碗米饭.",
                        "块 (kuài) is the everyday word for ringgit or dollars."),
                List.of(
                        lesson("At the canteen", 4,
                                List.of(w("吃", "chī", "to eat"), w("米饭", "mǐfàn", "rice"),
                                        w("面条", "miàntiáo", "noodles"), w("食堂", "shítáng", "canteen")),
                                List.of(s("我 去 食堂 吃饭", "。", "Wǒ qù shítáng chīfàn.", "I go to the canteen to eat.", null),
                                        s("我 要 一 碗 米饭", "。", "Wǒ yào yì wǎn mǐfàn.", "I'd like a bowl of rice.",
                                                "碗 (wǎn) is the measure word for bowls."))),
                        lesson("Ordering drinks", 4,
                                List.of(w("喝", "hē", "to drink"), w("茶", "chá", "tea"), w("咖啡", "kāfēi", "coffee"),
                                        w("水", "shuǐ", "water")),
                                List.of(s("我 要 一 杯 茶", "。", "Wǒ yào yì bēi chá.", "I'd like a cup of tea.",
                                                "杯 (bēi) is the measure word for cups and glasses."),
                                        s("你 喝 什么", "？", "Nǐ hē shénme?", "What would you like to drink?", null))),
                        lesson("Paying", 4,
                                List.of(w("钱", "qián", "money"), w("多少", "duōshao", "how much, how many"),
                                        w("块", "kuài", "ringgit, dollar"), w("贵", "guì", "expensive")),
                                List.of(s("多少 钱", "？", "Duōshao qián?", "How much is it?", null),
                                        s("这 个 太 贵 了", "。", "Zhège tài guì le.", "This one is too expensive.",
                                                "太……了 (tài … le) means too …"))),
                        lesson("Likes and dislikes", 4,
                                List.of(w("喜欢", "xǐhuan", "to like"), w("不", "bù", "not"),
                                        w("好吃", "hǎochī", "tasty"), w("辣", "là", "spicy")),
                                List.of(s("我 喜欢 吃 面条", "。", "Wǒ xǐhuan chī miàntiáo.", "I like eating noodles.", null),
                                        s("我 不 吃 辣", "。", "Wǒ bù chī là.", "I don't eat spicy food.",
                                                "Put 不 (bù) before a verb to say no."))),
                        bonus(List.of(w("菜单", "càidān", "menu"), w("饿", "è", "hungry"), w("渴", "kě", "thirsty"),
                                        w("买单", "mǎidān", "to pay the bill")),
                                List.of(s("我 饿 了", "。", "Wǒ è le.", "I'm hungry.", null))),
                        review(2)));
    }

    private static UnitSpec unit3() {
        return new UnitSpec(3, 1, "Getting around", "出行", 1, "map", PublishStatus.PUBLISHED,
                List.of(
                        p("你去哪里？", "nǐ qù nǎlǐ?", "where are you going?"),
                        p("……怎么走？", "…zěnme zǒu?", "how do I get to…?"),
                        p("……在哪儿？", "…zài nǎr?", "where is…?"),
                        p("请在这里停。", "qǐng zài zhèlǐ tíng.", "please stop here."),
                        p("一张票", "yì zhāng piào", "one ticket")),
                List.of(
                        "去 (qù) + place: where you are going.",
                        "在 (zài) + place: where something is.",
                        "张 (zhāng) is the measure word for tickets and other flat things."),
                List.of(
                        lesson("Asking the way", 4,
                                List.of(w("去", "qù", "to go"), w("哪里", "nǎlǐ", "where"), w("怎么", "zěnme", "how"),
                                        w("走", "zǒu", "to walk")),
                                List.of(s("你 去 哪里", "？", "Nǐ qù nǎlǐ?", "Where are you going?", null),
                                        s("图书馆 怎么 走", "？", "Túshūguǎn zěnme zǒu?", "How do I get to the library?",
                                                "Place + 怎么走 asks for the way."))),
                        lesson("Taking the LRT", 4,
                                List.of(w("坐", "zuò", "to take (a train or bus)"), w("站", "zhàn", "station"),
                                        w("车", "chē", "vehicle"), w("票", "piào", "ticket")),
                                List.of(s("我 在 这 站 下车", "。", "Wǒ zài zhè zhàn xià chē.", "I get off at this station.", null),
                                        s("一 张 票", "", "yì zhāng piào", "one ticket",
                                                "张 (zhāng) counts tickets."))),
                        lesson("Taxi and Grab", 4,
                                List.of(w("出租车", "chūzūchē", "taxi"), w("打车", "dǎchē", "to take a taxi"),
                                        w("火车站", "huǒchēzhàn", "train station"), w("这里", "zhèlǐ", "here")),
                                List.of(s("我 打车 去 火车站", "。", "Wǒ dǎchē qù huǒchēzhàn.",
                                                "I take a taxi to the train station.", null),
                                        s("请 在 这里 停", "。", "Qǐng zài zhèlǐ tíng.", "Please stop here.", null))),
                        lesson("Where is…?", 4,
                                List.of(w("在", "zài", "to be at"), w("哪儿", "nǎr", "where"),
                                        w("左边", "zuǒbian", "left side"), w("右边", "yòubian", "right side")),
                                List.of(s("厕所 在 哪儿", "？", "Cèsuǒ zài nǎr?", "Where is the toilet?", null),
                                        s("银行 在 右边", "。", "Yínháng zài yòubian.", "The bank is on the right.",
                                                "Thing + 在 + place says where it is."))),
                        bonus(List.of(w("近", "jìn", "near"), w("远", "yuǎn", "far"), w("地图", "dìtú", "map")),
                                List.of(s("学校 很 近", "。", "Xuéxiào hěn jìn.", "The school is close.", null))),
                        review(3)));
    }

    private static UnitSpec unit4() {
        return new UnitSpec(4, 1, "Campus life", "校园", 1, "classes", PublishStatus.PUBLISHED,
                List.of(
                        p("我学中文。", "wǒ xué Zhōngwén.", "I study Chinese."),
                        p("认识你很高兴。", "rènshi nǐ hěn gāoxìng.", "nice to meet you."),
                        p("图书馆", "túshūguǎn", "library"),
                        p("周末", "zhōumò", "weekend"),
                        p("一起", "yìqǐ", "together")),
                List.of(
                        "很 (hěn) links a person to an adjective: 我很高兴。",
                        "吧 (ba) at the end makes a suggestion: 我们一起去吧。",
                        "的 (de) shows belonging: 我的同学 is my classmate."),
                List.of(
                        lesson("My classes", 4,
                                List.of(w("老师", "lǎoshī", "teacher"), w("上课", "shàngkè", "to attend class"),
                                        w("中文", "Zhōngwén", "Chinese (language)"), w("学", "xué", "to study")),
                                List.of(s("我 学 中文", "。", "Wǒ xué Zhōngwén.", "I study Chinese.", null),
                                        s("我们 九 点 上课", "。", "Wǒmen jiǔ diǎn shàngkè.", "We have class at nine.",
                                                "The time goes before the verb."))),
                        lesson("Library and canteen", 4,
                                List.of(w("图书馆", "túshūguǎn", "library"), w("书", "shū", "book"),
                                        w("看", "kàn", "to look, to read"), w("宿舍", "sùshè", "dormitory")),
                                List.of(s("我 去 图书馆", "。", "Wǒ qù túshūguǎn.", "I go to the library.", null),
                                        s("我 在 图书馆 看 书", "。", "Wǒ zài túshūguǎn kàn shū.", "I read at the library.",
                                                "在 + place comes before the action."))),
                        lesson("Making friends", 4,
                                List.of(w("朋友", "péngyou", "friend"), w("认识", "rènshi", "to get to know"),
                                        w("高兴", "gāoxìng", "happy"), w("同学", "tóngxué", "classmate")),
                                List.of(s("认识 你 很 高兴", "。", "Rènshi nǐ hěn gāoxìng.", "Nice to meet you.", null),
                                        s("他 是 我 的 同学", "。", "Tā shì wǒ de tóngxué.", "He is my classmate.",
                                                "的 (de) shows belonging."))),
                        lesson("Weekend plans", 5,
                                List.of(w("周末", "zhōumò", "weekend"), w("电影", "diànyǐng", "film"),
                                        w("一起", "yìqǐ", "together"), w("想", "xiǎng", "to want to")),
                                List.of(s("周末 你 想 做 什么", "？", "Zhōumò nǐ xiǎng zuò shénme?",
                                                "What do you want to do at the weekend?", null),
                                        s("我们 一起 看 电影 吧", "。", "Wǒmen yìqǐ kàn diànyǐng ba.",
                                                "Let's watch a film together.", "吧 (ba) at the end makes a suggestion."))),
                        review(4),
                        new LessonSpec("Level 1 checkpoint", LearnLessonKind.CHECKPOINT, 10, CHECKPOINT_XP, List.of(), List.of())));
    }

    // ===== Level 2 · Beginner 2 · HSK 2 =====

    private static UnitSpec unit5() {
        return new UnitSpec(5, 2, "Shopping", "购物", 2, "shopping", PublishStatus.PUBLISHED,
                List.of(
                        p("我想买……", "wǒ xiǎng mǎi…", "I want to buy…"),
                        p("可以试一下吗？", "kěyǐ shì yíxià ma?", "can I try it on?"),
                        p("便宜一点儿吧。", "piányi yìdiǎnr ba.", "make it a bit cheaper."),
                        p("太贵了！", "tài guì le!", "too expensive!"),
                        p("我要这个。", "wǒ yào zhège.", "I'll take this one.")),
                List.of(
                        "件 (jiàn) counts clothes: 一件衣服.",
                        "一点儿 (yìdiǎnr) makes a request softer.",
                        "斤 (jīn) is half a kilogram at markets."),
                List.of(
                        lesson("Clothes and colours", 4,
                                List.of(w("衣服", "yīfu", "clothes"), w("红色", "hóngsè", "red"), w("白色", "báisè", "white"),
                                        w("颜色", "yánsè", "colour")),
                                List.of(s("我 想 买 一 件 衣服", "。", "Wǒ xiǎng mǎi yí jiàn yīfu.",
                                                "I want to buy a piece of clothing.", "件 (jiàn) counts clothes."),
                                        s("你 喜欢 什么 颜色", "？", "Nǐ xǐhuan shénme yánsè?", "What colour do you like?", null))),
                        lesson("Sizes", 4,
                                List.of(w("大", "dà", "big"), w("小", "xiǎo", "small"), w("试", "shì", "to try"),
                                        w("合适", "héshì", "to fit")),
                                List.of(s("我 可以 试 一下 吗", "？", "Wǒ kěyǐ shì yíxià ma?", "Can I try it on?", null),
                                        s("这 件 有点儿 大", "。", "Zhè jiàn yǒudiǎnr dà.", "This one is a bit big.",
                                                "有点儿 (yǒudiǎnr) + adjective: a bit too …"))),
                        lesson("Asking for a discount", 4,
                                List.of(w("便宜", "piányi", "cheap"), w("买", "mǎi", "to buy"), w("卖", "mài", "to sell"),
                                        w("一点儿", "yìdiǎnr", "a little")),
                                List.of(s("便宜 一点儿 吧", "。", "Piányi yìdiǎnr ba.", "Make it a bit cheaper.", null),
                                        s("这 个 怎么 卖", "？", "Zhège zěnme mài?", "How much is this sold for?",
                                                "买 (mǎi) is to buy, 卖 (mài) is to sell."))),
                        lesson("At the night market", 4,
                                List.of(w("夜市", "yèshì", "night market"), w("水果", "shuǐguǒ", "fruit"),
                                        w("苹果", "píngguǒ", "apple"), w("斤", "jīn", "half a kilogram")),
                                List.of(s("我 要 两 斤 苹果", "。", "Wǒ yào liǎng jīn píngguǒ.", "I'd like a kilo of apples.",
                                                "两斤 is two jīn: one kilogram."),
                                        s("夜市 很 热闹", "。", "Yèshì hěn rènao.", "The night market is lively.", null))),
                        bonus(List.of(w("鞋", "xié", "shoes"), w("帽子", "màozi", "hat"), w("钱包", "qiánbāo", "wallet")),
                                List.of(s("这 双 鞋 很 好看", "。", "Zhè shuāng xié hěn hǎokàn.", "These shoes look nice.",
                                        "双 (shuāng) counts pairs."))),
                        review(5)));
    }

    private static UnitSpec unit6() {
        return new UnitSpec(6, 2, "Time and dates", "时间", 2, "calendar", PublishStatus.PUBLISHED,
                List.of(
                        p("现在几点？", "xiànzài jǐ diǎn?", "what time is it?"),
                        p("今天星期几？", "jīntiān xīngqī jǐ?", "what day is it today?"),
                        p("生日快乐！", "shēngrì kuàilè!", "happy birthday!"),
                        p("你明天有空吗？", "nǐ míngtiān yǒu kòng ma?", "are you free tomorrow?"),
                        p("三点半", "sān diǎn bàn", "half past three")),
                List.of(
                        "The time goes before the verb: 我们三点见。",
                        "星期 + number: 星期一 is Monday, 星期天 is Sunday.",
                        "Dates go from big to small: year, month, day."),
                List.of(
                        lesson("Telling the time", 4,
                                List.of(w("点", "diǎn", "o'clock"), w("现在", "xiànzài", "now"), w("半", "bàn", "half"),
                                        w("几", "jǐ", "how many, which")),
                                List.of(s("现在 几 点", "？", "Xiànzài jǐ diǎn?", "What time is it now?", null),
                                        s("三 点 半", "", "sān diǎn bàn", "half past three", "半 (bàn) after 点 is half past."))),
                        lesson("Days of the week", 4,
                                List.of(w("星期一", "xīngqīyī", "Monday"), w("星期天", "xīngqītiān", "Sunday"),
                                        w("今天", "jīntiān", "today"), w("明天", "míngtiān", "tomorrow")),
                                List.of(s("今天 星期 几", "？", "Jīntiān xīngqī jǐ?", "What day is it today?", null),
                                        s("明天 是 星期六", "。", "Míngtiān shì xīngqīliù.", "Tomorrow is Saturday.", null))),
                        lesson("Dates and birthdays", 4,
                                List.of(w("月", "yuè", "month"), w("号", "hào", "day of the month"),
                                        w("生日", "shēngrì", "birthday"), w("年", "nián", "year")),
                                List.of(s("你 的 生日 是 几 月 几 号", "？", "Nǐ de shēngrì shì jǐ yuè jǐ hào?",
                                                "When is your birthday?", null),
                                        s("生日 快乐", "！", "Shēngrì kuàilè!", "Happy birthday!", null))),
                        lesson("Making plans to meet", 5,
                                List.of(w("有空", "yǒu kòng", "to be free"), w("见面", "jiànmiàn", "to meet"),
                                        w("晚上", "wǎnshang", "evening"), w("时间", "shíjiān", "time")),
                                List.of(s("你 明天 有空 吗", "？", "Nǐ míngtiān yǒu kòng ma?", "Are you free tomorrow?",
                                                "吗 (ma) at the end turns a sentence into a yes/no question."),
                                        s("我们 晚上 七 点 见面", "。", "Wǒmen wǎnshang qī diǎn jiànmiàn.",
                                                "Let's meet at seven in the evening.", null))),
                        bonus(List.of(w("早上", "zǎoshang", "morning"), w("中午", "zhōngwǔ", "noon"),
                                        w("下午", "xiàwǔ", "afternoon")),
                                List.of(s("我们 中午 见", "。", "Wǒmen zhōngwǔ jiàn.", "See you at noon.", null))),
                        review(6)));
    }

    private static UnitSpec unit7() {
        return new UnitSpec(7, 2, "Work and study", "工作", 2, "briefcase", PublishStatus.DRAFT,
                List.of(
                        p("我在打工。", "wǒ zài dǎgōng.", "I'm working part-time."),
                        p("我很忙。", "wǒ hěn máng.", "I'm busy."),
                        p("明天有考试。", "míngtiān yǒu kǎoshì.", "there's an exam tomorrow."),
                        p("加油！", "jiāyóu!", "you can do it!"),
                        p("我想当老师。", "wǒ xiǎng dāng lǎoshī.", "I want to be a teacher.")),
                List.of(
                        "在 (zài) + place + verb: where you do it.",
                        "当 (dāng) + job: to work as.",
                        "都 (dōu) means every time: 我每天都很忙。"),
                List.of(
                        lesson("Part-time jobs", 4,
                                List.of(w("工作", "gōngzuò", "work, job"), w("打工", "dǎgōng", "to work part-time"),
                                        w("每天", "měitiān", "every day"), w("忙", "máng", "busy")),
                                List.of(s("我 在 咖啡店 打工", "。", "Wǒ zài kāfēidiàn dǎgōng.", "I work part-time at a café.", null),
                                        s("我 每天 都 很 忙", "。", "Wǒ měitiān dōu hěn máng.", "I'm busy every day.", null))),
                        lesson("At the office", 4,
                                List.of(w("公司", "gōngsī", "company"), w("同事", "tóngshì", "colleague"),
                                        w("开会", "kāihuì", "to have a meeting"), w("电脑", "diànnǎo", "computer")),
                                List.of(s("我们 下午 开会", "。", "Wǒmen xiàwǔ kāihuì.", "We have a meeting this afternoon.", null),
                                        s("她 是 我 的 同事", "。", "Tā shì wǒ de tóngshì.", "She is my colleague.", null))),
                        lesson("Exams", 4,
                                List.of(w("考试", "kǎoshì", "exam"), w("复习", "fùxí", "to revise"), w("难", "nán", "difficult"),
                                        w("容易", "róngyì", "easy")),
                                List.of(s("明天 有 考试", "。", "Míngtiān yǒu kǎoshì.", "There's an exam tomorrow.", null),
                                        s("这 次 考试 不 难", "。", "Zhè cì kǎoshì bù nán.", "This exam isn't hard.", null))),
                        lesson("Future plans", 4,
                                List.of(w("以后", "yǐhòu", "in the future"), w("希望", "xīwàng", "to hope"),
                                        w("当", "dāng", "to work as"), w("医生", "yīshēng", "doctor")),
                                List.of(s("我 以后 想 当 老师", "。", "Wǒ yǐhòu xiǎng dāng lǎoshī.",
                                                "I want to be a teacher in the future.", null),
                                        s("我 希望 去 中国", "。", "Wǒ xīwàng qù Zhōngguó.", "I hope to go to China.", null))),
                        review(7)));
    }

    private static UnitSpec unit8() {
        return new UnitSpec(8, 2, "Travel", "旅行", 2, "globe", PublishStatus.PUBLISHED,
                List.of(
                        p("我的护照在这里。", "wǒ de hùzhào zài zhèlǐ.", "here is my passport."),
                        p("我想住两个晚上。", "wǒ xiǎng zhù liǎng ge wǎnshang.", "I'd like to stay two nights."),
                        p("你可以帮我吗？", "nǐ kěyǐ bāng wǒ ma?", "can you help me?"),
                        p("这里很漂亮！", "zhèlǐ hěn piàoliang!", "it's beautiful here!"),
                        p("我们拍张照片吧。", "wǒmen pāi zhāng zhàopiàn ba.", "let's take a photo.")),
                List.of(
                        "可以 (kěyǐ) asks for permission or help: 可以帮我吗？",
                        "吗 (ma) at the end turns a sentence into a yes/no question.",
                        "个 (ge) is the most common measure word: 两个晚上."),
                List.of(
                        lesson("At the airport", 4,
                                List.of(w("机场", "jīchǎng", "airport"), w("飞机", "fēijī", "plane"),
                                        w("护照", "hùzhào", "passport"), w("行李", "xíngli", "luggage")),
                                List.of(s("我 的 护照 在 这里", "。", "Wǒ de hùzhào zài zhèlǐ.", "My passport is here.", null),
                                        s("飞机 几 点 起飞", "？", "Fēijī jǐ diǎn qǐfēi?", "What time does the plane take off?", null))),
                        lesson("At the hotel", 4,
                                List.of(w("酒店", "jiǔdiàn", "hotel"), w("房间", "fángjiān", "room"), w("住", "zhù", "to stay"),
                                        w("钥匙", "yàoshi", "key")),
                                List.of(s("我 想 住 两 个 晚上", "。", "Wǒ xiǎng zhù liǎng ge wǎnshang.",
                                                "I'd like to stay two nights.", "两 (liǎng) is two before a measure word."),
                                        s("我 的 房间 在 三 楼", "。", "Wǒ de fángjiān zài sān lóu.",
                                                "My room is on the third floor.", null))),
                        lesson("Asking for help", 4,
                                List.of(w("帮", "bāng", "to help"), w("问题", "wèntí", "question, problem"),
                                        w("可以", "kěyǐ", "can, may"), w("请问", "qǐngwèn", "excuse me")),
                                List.of(s("你 可以 帮 我 吗", "？", "Nǐ kěyǐ bāng wǒ ma?", "Can you help me?", null),
                                        s("我 有 一 个 问题", "。", "Wǒ yǒu yí ge wèntí.", "I have a question.", null))),
                        lesson("Sightseeing", 4,
                                List.of(w("照片", "zhàopiàn", "photo"), w("拍", "pāi", "to take (a photo)"),
                                        w("漂亮", "piàoliang", "beautiful"), w("有名", "yǒumíng", "famous")),
                                List.of(s("这里 很 漂亮", "！", "Zhèlǐ hěn piàoliang!", "It's beautiful here!", null),
                                        s("我们 拍 一 张 照片 吧", "。", "Wǒmen pāi yì zhāng zhàopiàn ba.",
                                                "Let's take a photo.", null))),
                        bonus(List.of(w("旅行", "lǚxíng", "to travel"), w("海边", "hǎibiān", "seaside"),
                                        w("天气", "tiānqì", "weather")),
                                List.of(s("今天 天气 很 好", "。", "Jīntiān tiānqì hěn hǎo.", "The weather is nice today.", null))),
                        review(8)));
    }

    // ===== Builders =====

    private static Word w(String hanzi, String pinyin, String meaning) {
        return new Word(hanzi, pinyin, meaning);
    }

    private static Sentence s(String tiles, String punctuation, String pinyin, String meaning, String note) {
        return new Sentence(tiles, punctuation, pinyin, meaning, note);
    }

    private static Phrase p(String hanzi, String pinyin, String meaning) {
        return new Phrase(hanzi, pinyin, meaning);
    }

    private static LessonSpec lesson(String title, int minutes, List<Word> words, List<Sentence> sentences) {
        return new LessonSpec(title, LearnLessonKind.LESSON, minutes, LESSON_XP, words, sentences);
    }

    private static LessonSpec bonus(List<Word> words, List<Sentence> sentences) {
        return new LessonSpec("Bonus chest", LearnLessonKind.BONUS, 3, BONUS_XP, words, sentences);
    }

    private static LessonSpec review(int unit) {
        return new LessonSpec("Unit " + unit + " review", LearnLessonKind.REVIEW, 6, REVIEW_XP, List.of(), List.of());
    }
}
