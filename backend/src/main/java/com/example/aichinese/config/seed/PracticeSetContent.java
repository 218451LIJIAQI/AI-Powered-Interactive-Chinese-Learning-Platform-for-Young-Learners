package com.example.aichinese.config.seed;

import static com.example.aichinese.entity.enums.LearningStage.GUIDED;
import static com.example.aichinese.entity.enums.LearningStage.INDEPENDENT;
import static com.example.aichinese.entity.enums.LearningStage.SCAFFOLDED;

import java.util.List;
import java.util.stream.Stream;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * The Practice library that comes with the platform: five beginner sets of each type the Practice page shows (six
 * Flashcards sets). A set's XP follows the type's rule (RewardRules.practiceSetXp).
 */
final class PracticeSetContent {

    private PracticeSetContent() {
    }

    record SetSpec(PracticeType type, PracticeSetRequest request) {
    }

    static List<SetSpec> sets() {
        return Stream.of(flashcards(), listening(), tracing(), dragDrop(), matching(), typing())
                .flatMap(List::stream)
                .toList();
    }

    private static List<SetSpec> flashcards() {
        return List.of(
                set(PracticeType.FLASHCARDS, "Campus Words", GUIDED, 1, List.of(
                        card("图书馆", "túshūguǎn", "library", "我在图书馆看书。", "I read at the library."),
                        card("食堂", "shítáng", "canteen", "我们去食堂吃饭。", "We go to the canteen to eat."),
                        card("宿舍", "sùshè", "dormitory", "我住在宿舍。", "I live in the dorm."),
                        card("教室", "jiàoshì", "classroom", "教室在二楼。", "The classroom is on the second floor."),
                        card("老师", "lǎoshī", "teacher", "老师好！", "Hello, teacher!"),
                        card("同学", "tóngxué", "classmate", "他是我的同学。", "He is my classmate."))),
                set(PracticeType.FLASHCARDS, "Food and Drink", GUIDED, 1, List.of(
                        card("米饭", "mǐfàn", "rice", "我要一碗米饭。", "I'd like a bowl of rice."),
                        card("面条", "miàntiáo", "noodles", "我喜欢吃面条。", "I like noodles."),
                        card("茶", "chá", "tea", "我喝茶。", "I drink tea."),
                        card("咖啡", "kāfēi", "coffee", "一杯咖啡，谢谢。", "A coffee, please."),
                        card("水", "shuǐ", "water", "我要一杯水。", "I'd like a glass of water."),
                        card("菜单", "càidān", "menu", "请给我菜单。", "The menu, please."))),
                set(PracticeType.FLASHCARDS, "Numbers and Prices", GUIDED, 1, List.of(
                        card("一", "yī", "one", "一个人", "one person"),
                        card("二", "èr", "two", "二月", "February"),
                        card("三", "sān", "three", "三本书", "three books"),
                        card("十", "shí", "ten", "十块", "ten ringgit"),
                        card("块", "kuài", "ringgit, dollar", "五块钱", "five ringgit"),
                        card("多少钱", "duōshao qián", "how much (money)", "这个多少钱？", "How much is this?"))),
                set(PracticeType.FLASHCARDS, "Family", GUIDED, 1, List.of(
                        card("爸爸", "bàba", "dad", "我爸爸是医生。", "My dad is a doctor."),
                        card("妈妈", "māma", "mum", "这是我妈妈。", "This is my mum."),
                        card("哥哥", "gēge", "older brother", "我有一个哥哥。", "I have an older brother."),
                        card("姐姐", "jiějie", "older sister", "我姐姐在吉隆坡。", "My older sister is in Kuala Lumpur."),
                        card("弟弟", "dìdi", "younger brother", "我弟弟十岁。", "My younger brother is ten."),
                        card("妹妹", "mèimei", "younger sister", "妹妹喜欢猫。", "My younger sister likes cats."))),
                set(PracticeType.FLASHCARDS, "Greetings", GUIDED, 1, List.of(
                        card("你好", "nǐ hǎo", "hello", "你好！我叫美玲。", "Hello! My name is Mei Ling."),
                        card("谢谢", "xièxie", "thank you", "谢谢你的帮助。", "Thank you for your help."),
                        card("再见", "zàijiàn", "goodbye", "明天见，再见！", "See you tomorrow, goodbye!"),
                        card("对不起", "duìbuqǐ", "sorry", "对不起，我来晚了。", "Sorry, I'm late."),
                        card("不客气", "bú kèqi", "you're welcome", "不客气！", "You're welcome!"),
                        card("早上好", "zǎoshang hǎo", "good morning", "老师，早上好！", "Good morning, teacher!"))),
                set(PracticeType.FLASHCARDS, "Travel Words", SCAFFOLDED, 2, List.of(
                        card("机场", "jīchǎng", "airport", "我们去机场。", "We're going to the airport."),
                        card("飞机", "fēijī", "plane", "飞机几点起飞？", "What time does the plane take off?"),
                        card("酒店", "jiǔdiàn", "hotel", "酒店很近。", "The hotel is close."),
                        card("护照", "hùzhào", "passport", "我的护照在这里。", "My passport is here."),
                        card("地铁", "dìtiě", "underground train", "我坐地铁去。", "I'm going by underground train."),
                        card("出租车", "chūzūchē", "taxi", "我们坐出租车吧。", "Let's take a taxi."))));
    }

    private static List<SetSpec> listening() {
        String heard = "Which phrase did you hear?";
        String number = "Which number did you hear?";
        String digits = "Which digits did you hear?";
        return List.of(
                set(PracticeType.LISTENING, "At the Café", SCAFFOLDED, 1, List.of(
                        listen(heard, "一杯茶", "一杯茶 (yì bēi chá) is a cup of tea.",
                                0, han("一杯茶", "yì bēi chá"), han("一碗米饭", "yì wǎn mǐfàn"),
                                han("两杯咖啡", "liǎng bēi kāfēi"), han("一个包子", "yí ge bāozi")),
                        listen("What does she want?", "我要一杯咖啡。", "咖啡 (kāfēi) is coffee.",
                                1, text("A tea"), text("A coffee"), text("Some water"), text("Some rice")),
                        listen(heard, "两杯咖啡", "两杯咖啡 (liǎng bēi kāfēi) is two coffees.",
                                2, han("一杯咖啡", "yì bēi kāfēi"), han("两杯茶", "liǎng bēi chá"),
                                han("两杯咖啡", "liǎng bēi kāfēi"), han("两碗面条", "liǎng wǎn miàntiáo")),
                        listen("How much is it?", "十五块。", "十五块 (shíwǔ kuài) is fifteen ringgit.",
                                0, text("15 ringgit"), text("5 ringgit"), text("50 ringgit"), text("12 ringgit")),
                        listen("What did the customer ask for?", "不要辣。", "不要辣 (bú yào là) means no chilli.",
                                3, text("Very spicy, please."), text("No ice, please."), text("Less sugar, please."),
                                text("No chilli, please.")))),
                set(PracticeType.LISTENING, "Numbers You Hear", GUIDED, 1, List.of(
                        listen(number, "三", "三 (sān) is three.",
                                0, han("三", "sān"), han("四", "sì"), han("十", "shí"), han("七", "qī")),
                        listen(number, "八", "八 (bā) is eight.",
                                2, han("六", "liù"), han("一", "yī"), han("八", "bā"), han("九", "jiǔ")),
                        listen(number, "十二", "十二 (shí'èr) is twelve: ten and two.",
                                1, han("二十", "èrshí"), han("十二", "shí'èr"), han("十一", "shíyī"), han("十三", "shísān")),
                        listen(number, "五十", "五十 (wǔshí) is fifty: five tens.",
                                0, han("五十", "wǔshí"), han("十五", "shíwǔ"), han("四十", "sìshí"), han("五", "wǔ")),
                        listen(number, "九十九", "九十九 (jiǔshíjiǔ) is ninety-nine.",
                                3, han("十九", "shíjiǔ"), han("九十", "jiǔshí"), han("七十七", "qīshíqī"),
                                han("九十九", "jiǔshíjiǔ")))),
                set(PracticeType.LISTENING, "Asking the Way", SCAFFOLDED, 1, List.of(
                        listen("Where does he want to go?", "图书馆怎么走？", "图书馆 (túshūguǎn) is the library.",
                                0, text("The library"), text("The canteen"), text("The station"), text("The bank")),
                        listen("Which way is it?", "在左边。", "左边 (zuǒbian) is the left side.",
                                1, text("On the right"), text("On the left"), text("Straight ahead"), text("Behind you")),
                        listen(heard, "厕所在哪儿？", "厕所在哪儿 asks where the toilet is.",
                                2, han("银行在哪儿？", "yínháng zài nǎr?"), han("你去哪儿？", "nǐ qù nǎr?"),
                                han("厕所在哪儿？", "cèsuǒ zài nǎr?"), han("学校在哪儿？", "xuéxiào zài nǎr?")),
                        listen("Is it far?", "很近。", "近 (jìn) is near; 远 (yuǎn) is far.",
                                0, text("No, it's close."), text("Yes, it's very far."), text("It's on the right."),
                                text("It's closed.")),
                        listen("What should you do?", "一直走。", "一直走 (yìzhí zǒu) is go straight on.",
                                3, text("Turn left"), text("Turn right"), text("Go back"), text("Go straight on")))),
                set(PracticeType.LISTENING, "Phone Numbers", SCAFFOLDED, 1, List.of(
                        listen(digits, "零一二", "零 (líng) is zero.", 0, text("012"), text("021"), text("102"), text("120")),
                        listen(digits, "三四五", "三 (sān), 四 (sì), 五 (wǔ).", 1, text("354"), text("345"), text("435"), text("543")),
                        listen(digits, "六七八", "六 (liù), 七 (qī), 八 (bā).", 2, text("687"), text("768"), text("678"), text("867")),
                        listen(digits, "九零一", "九 (jiǔ), 零 (líng), 一 (yī).", 0, text("901"), text("910"), text("109"), text("190")),
                        listen(digits, "二二八", "二 (èr), 二 (èr), 八 (bā).", 3, text("282"), text("822"), text("288"), text("228")))),
                set(PracticeType.LISTENING, "Weather Today", INDEPENDENT, 2, List.of(
                        listen("What's the weather like?", "今天很热。", "热 (rè) is hot.",
                                0, text("Hot"), text("Cold"), text("Rainy"), text("Windy")),
                        listen("What happens tomorrow?", "明天下雨。", "下雨 (xià yǔ) is to rain.",
                                1, text("It's sunny"), text("It rains"), text("It snows"), text("It's windy")),
                        listen("How is the weather today?", "今天天气很好。", "天气很好 means the weather is nice.",
                                2, text("Bad"), text("Cold"), text("Nice"), text("Cloudy")),
                        listen("What's it like outside?", "外面很冷。", "冷 (lěng) is cold.",
                                0, text("Cold"), text("Hot"), text("Wet"), text("Dark")),
                        listen("What should you take?", "带伞吧。", "伞 (sǎn) is an umbrella; 带 (dài) is to take along.",
                                3, text("A hat"), text("A jacket"), text("Water"), text("An umbrella")))));
    }

    /** Writing: the characters with the strokes of each, in order. */
    private static List<SetSpec> tracing() {
        return List.of(
                write("Write 学", GUIDED, "学", "xué", "to learn", 8),
                write("Write 中文", GUIDED, "中文", "Zhōngwén", "Chinese (language)", 4, 4),
                write("Write 你好", GUIDED, "你好", "nǐ hǎo", "hello", 7, 6),
                write("Write 谢谢", SCAFFOLDED, "谢谢", "xièxie", "thank you", 12, 12),
                write("Write 日月", GUIDED, "日月", "rì yuè", "sun and moon", 4, 4));
    }

    /** Sentence building: the sentence's tiles in order, among a few extra tiles. */
    private static List<SetSpec> dragDrop() {
        return List.of(
                set(PracticeType.DRAG_DROP, "Places on Campus", SCAFFOLDED, 1, List.of(
                        sentence("我 去 图书馆", "Wǒ qù túshūguǎn.", "I go to the library.", "吃饭 学校"),
                        sentence("食堂 在 哪儿", "Shítáng zài nǎr?", "Where is the canteen?", "去 是"),
                        sentence("我 住 在 宿舍", "Wǒ zhù zài sùshè.", "I live in the dorm.", "去 教室"),
                        sentence("教室 在 二 楼", "Jiàoshì zài èr lóu.", "The classroom is on the second floor.", "三 是"))),
                set(PracticeType.DRAG_DROP, "Order a Drink", GUIDED, 1, List.of(
                        sentence("我 要 一 杯 茶", "Wǒ yào yì bēi chá.", "I'd like a cup of tea.", "碗 咖啡"),
                        sentence("你 喝 什么", "Nǐ hē shénme?", "What would you like to drink?", "吃 哪里"),
                        sentence("不 要 冰", "Bú yào bīng.", "No ice, please.", "辣 很"),
                        sentence("两 杯 咖啡", "Liǎng bēi kāfēi.", "Two coffees.", "二 碗"))),
                set(PracticeType.DRAG_DROP, "Ask the Way", SCAFFOLDED, 1, List.of(
                        sentence("银行 在 哪儿", "Yínháng zài nǎr?", "Where is the bank?", "去 是"),
                        sentence("图书馆 怎么 走", "Túshūguǎn zěnme zǒu?", "How do I get to the library?", "什么 坐"),
                        sentence("在 左边", "Zài zuǒbian.", "It's on the left.", "右边 是"),
                        sentence("请 一直 走", "Qǐng yìzhí zǒu.", "Please go straight on.", "坐 去"))),
                set(PracticeType.DRAG_DROP, "Tell the Time", SCAFFOLDED, 2, List.of(
                        sentence("现在 几 点", "Xiànzài jǐ diǎn?", "What time is it now?", "多少 号"),
                        sentence("现在 三 点 半", "Xiànzài sān diǎn bàn.", "It's half past three.", "号 星期"),
                        sentence("我 八 点 上课", "Wǒ bā diǎn shàngkè.", "I have class at eight.", "下课 号"),
                        sentence("我们 七 点 见", "Wǒmen qī diǎn jiàn.", "See you at seven.", "看 半"))),
                set(PracticeType.DRAG_DROP, "Make Plans", INDEPENDENT, 2, List.of(
                        sentence("你 明天 有空 吗", "Nǐ míngtiān yǒu kòng ma?", "Are you free tomorrow?", "昨天 呢"),
                        sentence("我们 一起 看 电影 吧", "Wǒmen yìqǐ kàn diànyǐng ba.", "Let's watch a film together.", "吃 吗"),
                        sentence("周末 去 海边", "Zhōumò qù hǎibiān.", "Go to the beach at the weekend.", "明天 吃"),
                        sentence("好 主意", "Hǎo zhǔyi!", "Good idea!", "坏 问题"))));
    }

    private static List<SetSpec> matching() {
        return List.of(
                set(PracticeType.MATCHING, "Numbers Match", GUIDED, 1, List.of(
                        pair("三", "sān", "three"), pair("五", "wǔ", "five"), pair("八", "bā", "eight"),
                        pair("十", "shí", "ten"))),
                set(PracticeType.MATCHING, "Family Words", GUIDED, 1, List.of(
                        pair("爸爸", "bàba", "dad"), pair("妈妈", "māma", "mum"), pair("哥哥", "gēge", "older brother"),
                        pair("妹妹", "mèimei", "younger sister"))),
                set(PracticeType.MATCHING, "Places on Campus", SCAFFOLDED, 1, List.of(
                        pair("图书馆", "túshūguǎn", "library"), pair("食堂", "shítáng", "canteen"),
                        pair("宿舍", "sùshè", "dormitory"), pair("教室", "jiàoshì", "classroom"))),
                set(PracticeType.MATCHING, "Colours", SCAFFOLDED, 2, List.of(
                        pair("红色", "hóngsè", "red"), pair("白色", "báisè", "white"), pair("黑色", "hēisè", "black"),
                        pair("蓝色", "lánsè", "blue"))),
                set(PracticeType.MATCHING, "Days of the Week", SCAFFOLDED, 2, List.of(
                        pair("星期一", "xīngqīyī", "Monday"), pair("星期三", "xīngqīsān", "Wednesday"),
                        pair("星期五", "xīngqīwǔ", "Friday"), pair("星期天", "xīngqītiān", "Sunday"))));
    }

    /** Pinyin to characters: the meaning is shown, the student types the pinyin and picks the word. */
    private static List<SetSpec> typing() {
        return List.of(
                set(PracticeType.TYPING, "Greetings", GUIDED, 1, List.of(
                        word("Thank you", "谢谢", "xièxie", "写写 歇歇 谢 写"),
                        word("Hello", "你好", "nǐ hǎo", "你 尼好 拟好"),
                        word("Goodbye", "再见", "zàijiàn", "在见 再 在"),
                        word("Sorry", "对不起", "duìbuqǐ", "对 不起 队不起"),
                        word("Teacher", "老师", "lǎoshī", "老是 老实 老"),
                        word("Classmate", "同学", "tóngxué", "同 同穴 铜学"))),
                set(PracticeType.TYPING, "Numbers", GUIDED, 1, List.of(
                        word("Three", "三", "sān", "伞 散 山"),
                        word("Ten", "十", "shí", "是 时 石"),
                        word("Four", "四", "sì", "是 死 寺"),
                        word("How much (money)", "多少钱", "duōshao qián", "多少 多 朵少钱"),
                        word("Ringgit, dollar", "块", "kuài", "快 会 筷"),
                        word("Two (of something)", "两", "liǎng", "量 亮 凉"))),
                set(PracticeType.TYPING, "Food", SCAFFOLDED, 1, List.of(
                        word("Tea", "茶", "chá", "查 差 叉"),
                        word("Rice", "米饭", "mǐfàn", "米 迷饭 米范"),
                        word("Water", "水", "shuǐ", "谁 睡 税"),
                        word("Menu", "菜单", "càidān", "菜 才单 彩单"),
                        word("Coffee", "咖啡", "kāfēi", "卡飞 咖 卡"),
                        word("Noodles", "面条", "miàntiáo", "面 免条 棉条"))),
                set(PracticeType.TYPING, "Campus", SCAFFOLDED, 2, List.of(
                        word("Library", "图书馆", "túshūguǎn", "图书 涂书馆 图"),
                        word("Canteen", "食堂", "shítáng", "实堂 食 时堂"),
                        word("Dormitory", "宿舍", "sùshè", "宿 速舍 诉舍"),
                        word("Classroom", "教室", "jiàoshì", "叫是 教 交室"),
                        word("Exam", "考试", "kǎoshì", "考 靠试 烤试"),
                        word("Homework", "作业", "zuòyè", "做业 作 坐业"))),
                set(PracticeType.TYPING, "Mixed Words", INDEPENDENT, 1, List.of(
                        word("Home, family", "家", "jiā", "加 佳 夹"),
                        word("Book", "书", "shū", "输 叔 舒"),
                        word("Money", "钱", "qián", "前 潜 钳"),
                        word("Friend", "朋友", "péngyou", "朋 碰友 朋有"),
                        word("To like", "喜欢", "xǐhuan", "喜 洗欢 西欢"),
                        word("To eat (a meal)", "吃饭", "chīfàn", "吃 迟饭 吃反"),
                        word("Student", "学生", "xuésheng", "学 雪生 学声"),
                        word("Tomorrow", "明天", "míngtiān", "明 名天 鸣天"))));
    }

    private static SetSpec set(PracticeType type, String title, LearningStage stage, int hsk, List<PracticeItemData> items) {
        return new SetSpec(type, new PracticeSetRequest(title, stage, hsk, items, null, null));
    }

    private static SetSpec write(String title, LearningStage stage, String characters, String pinyin, String meaning,
            Integer... strokesPerCharacter) {
        List<Integer> strokes = List.of(strokesPerCharacter);
        return new SetSpec(PracticeType.TRACING, new PracticeSetRequest(title, stage, 1,
                List.of(pair(characters, pinyin, meaning)), strokes.stream().mapToInt(Integer::intValue).sum(), strokes));
    }

    private static PracticeItemData card(String hanzi, String pinyin, String meaning, String example, String exampleMeaning) {
        return new PracticeItemData(hanzi, pinyin, meaning, null, null, example, exampleMeaning, List.of(), null, null,
                null);
    }

    private static PracticeItemData listen(String prompt, String audio, String explanation, int correct,
            ChoiceOption... options) {
        return new PracticeItemData(null, null, null, prompt, audio, null, null, List.of(options), correct,
                explanation, null);
    }

    private static PracticeItemData sentence(String tiles, String pinyin, String meaning, String distractors) {
        return new PracticeItemData(tiles, pinyin, meaning, null, null, null, null, List.of(), null, null, distractors);
    }

    /** A typing word: the meaning shown, the answer and its pinyin, and look-alike words typed the same way. */
    private static PracticeItemData word(String meaning, String hanzi, String pinyin, String decoys) {
        return new PracticeItemData(hanzi, pinyin, meaning, null, null, null, null, List.of(), null, null, decoys);
    }

    private static PracticeItemData pair(String hanzi, String pinyin, String meaning) {
        return new PracticeItemData(hanzi, pinyin, meaning, null, null, null, null, List.of(), null, null, null);
    }

    private static ChoiceOption text(String text) {
        return new ChoiceOption(text, null);
    }

    private static ChoiceOption han(String hanzi, String pinyin) {
        return new ChoiceOption(hanzi, pinyin);
    }
}
