package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.List;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.dto.learn.StoryLine;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/** The Practice library that comes with the platform: five beginner sets of each of the seven types. */
final class PracticeSetContent {

    private static final LearningStage GUIDED = LearningStage.GUIDED;
    private static final LearningStage SUPPORTED = LearningStage.SCAFFOLDED;
    private static final LearningStage INDEPENDENT = LearningStage.INDEPENDENT;

    private PracticeSetContent() {
    }

    record SetSpec(PracticeType type, PracticeSetRequest request) {
    }

    static List<SetSpec> sets() {
        List<SetSpec> sets = new ArrayList<>();
        sets.addAll(flashcards());
        sets.addAll(quizzes());
        sets.addAll(listening());
        sets.addAll(tracing());
        sets.addAll(dragDrop());
        sets.addAll(matching());
        sets.addAll(stories());
        return sets;
    }

    // ===== Flashcards =====

    private static List<SetSpec> flashcards() {
        return List.of(
                set(PracticeType.FLASHCARDS, "Campus Words", GUIDED, 1, 10, List.of(
                        card("图书馆", "túshūguǎn", "library", "我在图书馆看书。", "I read at the library."),
                        card("食堂", "shítáng", "canteen", "我们去食堂吃饭。", "We go to the canteen to eat."),
                        card("宿舍", "sùshè", "dormitory", "我住在宿舍。", "I live in the dorm."),
                        card("教室", "jiàoshì", "classroom", "教室在二楼。", "The classroom is on the second floor."),
                        card("老师", "lǎoshī", "teacher", "老师好！", "Hello, teacher!"),
                        card("同学", "tóngxué", "classmate", "他是我的同学。", "He is my classmate."))),
                set(PracticeType.FLASHCARDS, "Food and Drink", GUIDED, 1, 10, List.of(
                        card("米饭", "mǐfàn", "rice", "我要一碗米饭。", "I'd like a bowl of rice."),
                        card("面条", "miàntiáo", "noodles", "我喜欢吃面条。", "I like noodles."),
                        card("茶", "chá", "tea", "我喝茶。", "I drink tea."),
                        card("咖啡", "kāfēi", "coffee", "一杯咖啡，谢谢。", "A coffee, please."),
                        card("水", "shuǐ", "water", "我要一杯水。", "I'd like a glass of water."),
                        card("菜单", "càidān", "menu", "请给我菜单。", "The menu, please."))),
                set(PracticeType.FLASHCARDS, "Numbers and Prices", GUIDED, 1, 10, List.of(
                        card("一", "yī", "one", "一个人", "one person"),
                        card("二", "èr", "two", "二月", "February"),
                        card("三", "sān", "three", "三本书", "three books"),
                        card("十", "shí", "ten", "十块", "ten ringgit"),
                        card("块", "kuài", "ringgit, dollar", "五块钱", "five ringgit"),
                        card("多少钱", "duōshao qián", "how much (money)", "这个多少钱？", "How much is this?"))),
                set(PracticeType.FLASHCARDS, "Family", GUIDED, 1, 10, List.of(
                        card("爸爸", "bàba", "dad", "我爸爸是医生。", "My dad is a doctor."),
                        card("妈妈", "māma", "mum", "这是我妈妈。", "This is my mum."),
                        card("哥哥", "gēge", "older brother", "我有一个哥哥。", "I have an older brother."),
                        card("姐姐", "jiějie", "older sister", "我姐姐在吉隆坡。", "My older sister is in Kuala Lumpur."),
                        card("弟弟", "dìdi", "younger brother", "我弟弟十岁。", "My younger brother is ten."),
                        card("妹妹", "mèimei", "younger sister", "妹妹喜欢猫。", "My younger sister likes cats."))),
                set(PracticeType.FLASHCARDS, "Travel Words", SUPPORTED, 2, 10, List.of(
                        card("机场", "jīchǎng", "airport", "我们去机场。", "We're going to the airport."),
                        card("飞机", "fēijī", "plane", "飞机几点起飞？", "What time does the plane take off?"),
                        card("酒店", "jiǔdiàn", "hotel", "酒店很近。", "The hotel is close."),
                        card("护照", "hùzhào", "passport", "我的护照在这里。", "My passport is here."),
                        card("地铁", "dìtiě", "underground train", "我坐地铁去。", "I'm going by underground train."),
                        card("出租车", "chūzūchē", "taxi", "我们坐出租车吧。", "Let's take a taxi."))));
    }

    // ===== Quizzes =====

    private static List<SetSpec> quizzes() {
        return List.of(
                set(PracticeType.QUIZZES, "Greetings Quiz", GUIDED, 1, 15, List.of(
                        quiz("What does 你好 mean?", "你好", "nǐ hǎo", "你好 (nǐ hǎo) is the everyday hello.",
                                0, text("Hello"), text("Goodbye"), text("Thank you"), text("Sorry")),
                        quiz("What does 再见 mean?", "再见", "zàijiàn", "再见 (zàijiàn) means goodbye.",
                                1, text("Hello"), text("Goodbye"), text("Good morning"), text("Please")),
                        quiz("Which one means “thank you”?", null, null, "谢谢 (xièxie) means thank you.",
                                0, han("谢谢", "xièxie"), han("对不起", "duìbuqǐ"), han("没关系", "méi guānxi"), han("请", "qǐng")),
                        quiz("What does 早上好 mean?", "早上好", "zǎoshang hǎo", "早上 is morning, so 早上好 is good morning.",
                                2, text("Good evening"), text("Good night"), text("Good morning"), text("See you tomorrow")),
                        quiz("How do you answer 谢谢?", null, null, "不客气 (bú kèqi) means you're welcome.",
                                3, han("对不起", "duìbuqǐ"), han("再见", "zàijiàn"), han("你好", "nǐ hǎo"), han("不客气", "bú kèqi")),
                        quiz("What does 对不起 mean?", "对不起", "duìbuqǐ", "对不起 (duìbuqǐ) is sorry. 没关系 answers it.",
                                0, text("Sorry"), text("It's OK"), text("Please"), text("Thank you")))),
                set(PracticeType.QUIZZES, "Numbers Quiz", GUIDED, 1, 15, List.of(
                        quiz("What does 三 mean?", "三", "sān", "三 (sān) is three.",
                                0, text("three"), text("five"), text("eight"), text("ten")),
                        quiz("Which one is “ten”?", null, null, "十 (shí) is ten.",
                                1, han("七", "qī"), han("十", "shí"), han("一", "yī"), han("九", "jiǔ")),
                        quiz("What does 八 mean?", "八", "bā", "八 (bā) is eight.",
                                2, text("six"), text("nine"), text("eight"), text("four")),
                        quiz("How do you say “two”?", null, null, "二 (èr) is two.",
                                0, han("二", "èr"), han("三", "sān"), han("五", "wǔ"), han("六", "liù")),
                        quiz("What does 十五 mean?", "十五", "shíwǔ", "十五 is ten and five: fifteen.",
                                1, text("fifty"), text("fifteen"), text("five"), text("twenty-five")),
                        quiz("What does 二十 mean?", "二十", "èrshí", "二十 is two tens: twenty.",
                                3, text("twelve"), text("two"), text("two hundred"), text("twenty")))),
                set(PracticeType.QUIZZES, "Food Quiz", SUPPORTED, 1, 15, List.of(
                        quiz("What does 菜单 mean?", "菜单", "càidān", "菜单 (càidān) is the menu. The bill is 买单 (mǎidān).",
                                1, text("Bill"), text("Menu"), text("Rice"), text("Cup")),
                        quiz("What does 米饭 mean?", "米饭", "mǐfàn", "米饭 (mǐfàn) is cooked rice.",
                                0, text("Rice"), text("Noodles"), text("Bread"), text("Soup")),
                        quiz("Which one means “coffee”?", null, null, "咖啡 (kāfēi) sounds like coffee.",
                                2, han("茶", "chá"), han("水", "shuǐ"), han("咖啡", "kāfēi"), han("果汁", "guǒzhī")),
                        quiz("What does 好吃 mean?", "好吃", "hǎochī", "好吃 is good to eat: tasty.",
                                0, text("Tasty"), text("Hungry"), text("Expensive"), text("Spicy")),
                        quiz("What does 我要一杯茶 mean?", "我要一杯茶", "wǒ yào yì bēi chá", "我要 is I'd like, 一杯茶 a cup of tea.",
                                1, text("I don't drink tea."), text("I'd like a cup of tea."), text("The tea is hot."),
                                text("Tea is cheap here.")),
                        quiz("Which one means “spicy”?", null, null, "辣 (là) is spicy.",
                                3, han("甜", "tián"), han("贵", "guì"), han("饿", "è"), han("辣", "là")))),
                set(PracticeType.QUIZZES, "Campus Quiz", SUPPORTED, 1, 15, List.of(
                        quiz("What does 图书馆 mean?", "图书馆", "túshūguǎn", "图书馆 is a hall (馆) of books (书): the library.",
                                0, text("Library"), text("Canteen"), text("Dormitory"), text("Classroom")),
                        quiz("What does 同学 mean?", "同学", "tóngxué", "同学 (tóngxué) is someone who studies with you.",
                                2, text("Teacher"), text("Friend"), text("Classmate"), text("Student card")),
                        quiz("Which one means “dormitory”?", null, null, "宿舍 (sùshè) is where students live.",
                                0, han("宿舍", "sùshè"), han("食堂", "shítáng"), han("教室", "jiàoshì"), han("图书馆", "túshūguǎn")),
                        quiz("What does 上课 mean?", "上课", "shàngkè", "上课 is to go to class; 下课 is when class ends.",
                                1, text("To finish class"), text("To attend class"), text("To eat lunch"), text("To go home")),
                        quiz("What does 我学中文 mean?", "我学中文", "wǒ xué Zhōngwén", "学 (xué) is to study.",
                                0, text("I study Chinese."), text("I teach Chinese."), text("I am Chinese."), text("I like China.")),
                        quiz("What does 考试 mean?", "考试", "kǎoshì", "考试 (kǎoshì) is an exam.",
                                3, text("Homework"), text("Holiday"), text("Lesson"), text("Exam")))),
                set(PracticeType.QUIZZES, "Mixed Review", INDEPENDENT, 1, 15, List.of(
                        quiz("What does 认识你很高兴 mean?", "认识你很高兴", "rènshi nǐ hěn gāoxìng",
                                "认识 is to get to know, 高兴 is happy: nice to meet you.",
                                0, text("Nice to meet you."), text("See you tomorrow."), text("I'm very busy."),
                                text("You're welcome.")),
                        quiz("Which one means “how much”?", null, null, "多少 (duōshao) asks how much or how many.",
                                1, han("什么", "shénme"), han("多少", "duōshao"), han("哪里", "nǎlǐ"), han("怎么", "zěnme")),
                        quiz("What does 你去哪里？ mean?", "你去哪里？", "nǐ qù nǎlǐ?", "去 is to go, 哪里 is where.",
                                0, text("Where are you going?"), text("What are you doing?"), text("Who are you?"),
                                text("When are you going?")),
                        quiz("What does 周末 mean?", "周末", "zhōumò", "周末 is the end (末) of the week (周).",
                                2, text("Weekday"), text("Tomorrow"), text("Weekend"), text("Evening")),
                        quiz("Which one means “family, home”?", null, null, "家 (jiā) is family and home.",
                                0, han("家", "jiā"), han("车", "chē"), han("书", "shū"), han("钱", "qián")),
                        quiz("What does 我不吃辣 mean?", "我不吃辣", "wǒ bù chī là", "不 before 吃 says you don't eat it.",
                                1, text("I don't like noodles."), text("I don't eat spicy food."), text("I'm not hungry."),
                                text("The food is not spicy.")))));
    }

    // ===== Listening =====

    private static List<SetSpec> listening() {
        String heard = "Which phrase did you hear?";
        String number = "Which number did you hear?";
        String digits = "Which digits did you hear?";
        return List.of(
                set(PracticeType.LISTENING, "At the Café", SUPPORTED, 1, 15, List.of(
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
                set(PracticeType.LISTENING, "Numbers You Hear", GUIDED, 1, 15, List.of(
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
                set(PracticeType.LISTENING, "Asking the Way", SUPPORTED, 1, 15, List.of(
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
                set(PracticeType.LISTENING, "Phone Numbers", SUPPORTED, 1, 15, List.of(
                        listen(digits, "零一二", "零 (líng) is zero.", 0, text("012"), text("021"), text("102"), text("120")),
                        listen(digits, "三四五", "三 (sān), 四 (sì), 五 (wǔ).", 1, text("354"), text("345"), text("435"), text("543")),
                        listen(digits, "六七八", "六 (liù), 七 (qī), 八 (bā).", 2, text("687"), text("768"), text("678"), text("867")),
                        listen(digits, "九零一", "九 (jiǔ), 零 (líng), 一 (yī).", 0, text("901"), text("910"), text("109"), text("190")),
                        listen(digits, "二二八", "二 (èr), 二 (èr), 八 (bā).", 3, text("282"), text("822"), text("288"), text("228")))),
                set(PracticeType.LISTENING, "Weather Today", INDEPENDENT, 2, 15, List.of(
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

    // ===== Tracing =====

    private static List<SetSpec> tracing() {
        return List.of(
                write("Write 学", GUIDED, "学", "xué", "to learn", 8),
                write("Write 中文", GUIDED, "中文", "Zhōngwén", "Chinese (language)", 8),
                write("Write 你好", GUIDED, "你好", "nǐ hǎo", "hello", 13),
                write("Write 谢谢", SUPPORTED, "谢谢", "xièxie", "thank you", 24),
                write("Write 日月", GUIDED, "日月", "rì yuè", "sun and moon", 8));
    }

    // ===== Drag & Drop (sentence building) =====

    private static List<SetSpec> dragDrop() {
        return List.of(
                set(PracticeType.DRAG_DROP, "Places on Campus", SUPPORTED, 1, 15, List.of(
                        sentence("我 去 图书馆", "Wǒ qù túshūguǎn.", "I go to the library.", "吃饭 学校"),
                        sentence("食堂 在 哪儿", "Shítáng zài nǎr?", "Where is the canteen?", "去 是"),
                        sentence("我 住 在 宿舍", "Wǒ zhù zài sùshè.", "I live in the dorm.", "去 教室"),
                        sentence("教室 在 二 楼", "Jiàoshì zài èr lóu.", "The classroom is on the second floor.", "三 是"))),
                set(PracticeType.DRAG_DROP, "Order a Drink", GUIDED, 1, 15, List.of(
                        sentence("我 要 一 杯 茶", "Wǒ yào yì bēi chá.", "I'd like a cup of tea.", "碗 咖啡"),
                        sentence("你 喝 什么", "Nǐ hē shénme?", "What would you like to drink?", "吃 哪里"),
                        sentence("不 要 冰", "Bú yào bīng.", "No ice, please.", "辣 很"),
                        sentence("两 杯 咖啡", "Liǎng bēi kāfēi.", "Two coffees.", "二 碗"))),
                set(PracticeType.DRAG_DROP, "Ask the Way", SUPPORTED, 1, 15, List.of(
                        sentence("银行 在 哪儿", "Yínháng zài nǎr?", "Where is the bank?", "去 是"),
                        sentence("图书馆 怎么 走", "Túshūguǎn zěnme zǒu?", "How do I get to the library?", "什么 坐"),
                        sentence("在 左边", "Zài zuǒbian.", "It's on the left.", "右边 是"),
                        sentence("请 一直 走", "Qǐng yìzhí zǒu.", "Please go straight on.", "坐 去"))),
                set(PracticeType.DRAG_DROP, "Tell the Time", SUPPORTED, 2, 15, List.of(
                        sentence("现在 几 点", "Xiànzài jǐ diǎn?", "What time is it now?", "多少 号"),
                        sentence("现在 三 点 半", "Xiànzài sān diǎn bàn.", "It's half past three.", "号 星期"),
                        sentence("我 八 点 上课", "Wǒ bā diǎn shàngkè.", "I have class at eight.", "下课 号"),
                        sentence("我们 七 点 见", "Wǒmen qī diǎn jiàn.", "See you at seven.", "看 半"))),
                set(PracticeType.DRAG_DROP, "Make Plans", INDEPENDENT, 2, 15, List.of(
                        sentence("你 明天 有空 吗", "Nǐ míngtiān yǒu kòng ma?", "Are you free tomorrow?", "昨天 呢"),
                        sentence("我们 一起 看 电影 吧", "Wǒmen yìqǐ kàn diànyǐng ba.", "Let's watch a film together.", "吃 吗"),
                        sentence("周末 去 海边", "Zhōumò qù hǎibiān.", "Go to the beach at the weekend.", "明天 吃"),
                        sentence("好 主意", "Hǎo zhǔyi!", "Good idea!", "坏 问题"))));
    }

    // ===== Matching =====

    private static List<SetSpec> matching() {
        return List.of(
                set(PracticeType.MATCHING, "Numbers Match", GUIDED, 1, 15, List.of(
                        pair("三", "sān", "three"), pair("五", "wǔ", "five"), pair("八", "bā", "eight"),
                        pair("十", "shí", "ten"))),
                set(PracticeType.MATCHING, "Family Words", GUIDED, 1, 15, List.of(
                        pair("爸爸", "bàba", "dad"), pair("妈妈", "māma", "mum"), pair("哥哥", "gēge", "older brother"),
                        pair("妹妹", "mèimei", "younger sister"))),
                set(PracticeType.MATCHING, "Places on Campus", SUPPORTED, 1, 15, List.of(
                        pair("图书馆", "túshūguǎn", "library"), pair("食堂", "shítáng", "canteen"),
                        pair("宿舍", "sùshè", "dormitory"), pair("教室", "jiàoshì", "classroom"))),
                set(PracticeType.MATCHING, "Colours", SUPPORTED, 2, 15, List.of(
                        pair("红色", "hóngsè", "red"), pair("白色", "báisè", "white"), pair("黑色", "hēisè", "black"),
                        pair("蓝色", "lánsè", "blue"))),
                set(PracticeType.MATCHING, "Days of the Week", SUPPORTED, 2, 15, List.of(
                        pair("星期一", "xīngqīyī", "Monday"), pair("星期三", "xīngqīsān", "Wednesday"),
                        pair("星期五", "xīngqīwǔ", "Friday"), pair("星期天", "xīngqītiān", "Sunday"))));
    }

    // ===== Stories =====

    private static List<SetSpec> stories() {
        return List.of(
                story("A Day at the Library", SUPPORTED, 2,
                        List.of(
                                line("早上我去图书馆。", "Zǎoshang wǒ qù túshūguǎn.", "In the morning I go to the library."),
                                line("我看书，也写汉字。", "Wǒ kàn shū, yě xiě Hànzì.", "I read and I also write characters."),
                                line("中午我和朋友吃饭。", "Zhōngwǔ wǒ hé péngyou chīfàn.", "At noon I eat with a friend.")),
                        List.of(
                                quiz("Where does she go in the morning?", null, null,
                                        "早上我去图书馆: in the morning she goes to the library.",
                                        0, text("Library"), text("Canteen"), text("Classroom")),
                                quiz("What does she do at noon?", null, null,
                                        "中午我和朋友吃饭: at noon she eats with a friend.",
                                        1, text("Reads"), text("Eats with a friend"), text("Writes")))),
                story("Lunch at the Canteen", SUPPORTED, 1,
                        List.of(
                                line("我饿了。", "Wǒ è le.", "I'm hungry."),
                                line("我和小美去食堂。", "Wǒ hé Xiǎoměi qù shítáng.", "Xiaomei and I go to the canteen."),
                                line("我要一碗面条，她要米饭。", "Wǒ yào yì wǎn miàntiáo, tā yào mǐfàn.",
                                        "I want a bowl of noodles; she wants rice."),
                                line("面条很好吃！", "Miàntiáo hěn hǎochī!", "The noodles are tasty!")),
                        List.of(
                                quiz("Where do they go?", null, null, "我和小美去食堂: they go to the canteen.",
                                        0, text("The canteen"), text("The library"), text("A café")),
                                quiz("What does Xiaomei want?", null, null, "她要米饭: she wants rice.",
                                        1, text("Noodles"), text("Rice"), text("Tea")))),
                story("Taking the LRT", SUPPORTED, 2,
                        List.of(
                                line("今天我坐轻快铁去学校。", "Jīntiān wǒ zuò qīngkuàitiě qù xuéxiào.", "Today I take the LRT to school."),
                                line("车站离我家很近。", "Chēzhàn lí wǒ jiā hěn jìn.", "The station is close to my home."),
                                line("我买了一张票。", "Wǒ mǎi le yì zhāng piào.", "I bought a ticket."),
                                line("二十分钟以后，我到了。", "Èrshí fēnzhōng yǐhòu, wǒ dào le.", "Twenty minutes later, I arrived.")),
                        List.of(
                                quiz("How does he go to school?", null, null, "坐轻快铁 is to take the LRT.",
                                        0, text("By LRT"), text("By taxi"), text("On foot")),
                                quiz("How long is the trip?", null, null, "二十分钟 is twenty minutes.",
                                        2, text("10 minutes"), text("2 hours"), text("20 minutes")))),
                story("At the Pasar Malam", INDEPENDENT, 2,
                        List.of(
                                line("星期五晚上，我们去夜市。", "Xīngqīwǔ wǎnshang, wǒmen qù yèshì.",
                                        "On Friday evening we go to the night market."),
                                line("夜市很热闹。", "Yèshì hěn rènao.", "The night market is lively."),
                                line("我买了两斤苹果。", "Wǒ mǎi le liǎng jīn píngguǒ.", "I bought a kilo of apples."),
                                line("朋友说：“太便宜了！”", "Péngyou shuō: “Tài piányi le!”", "My friend says: “So cheap!”")),
                        List.of(
                                quiz("When do they go?", null, null, "星期五晚上 is Friday evening.",
                                        0, text("Friday evening"), text("Saturday morning"), text("Sunday afternoon")),
                                quiz("What does the friend think?", null, null, "太便宜了 means it is very cheap.",
                                        1, text("It's too expensive"), text("It's very cheap"), text("It's too busy")))),
                story("My Weekend", INDEPENDENT, 2,
                        List.of(
                                line("周末我不上课。", "Zhōumò wǒ bú shàngkè.", "At the weekend I have no classes."),
                                line("星期六我在咖啡店打工。", "Xīngqīliù wǒ zài kāfēidiàn dǎgōng.",
                                        "On Saturday I work part-time at a café."),
                                line("星期天我和家人去海边。", "Xīngqītiān wǒ hé jiārén qù hǎibiān.",
                                        "On Sunday I go to the beach with my family."),
                                line("天气很好，我很高兴。", "Tiānqì hěn hǎo, wǒ hěn gāoxìng.", "The weather is nice and I'm happy.")),
                        List.of(
                                quiz("What does she do on Saturday?", null, null, "星期六我在咖啡店打工: she works at a café.",
                                        0, text("Works at a café"), text("Goes to class"), text("Goes to the beach")),
                                quiz("Who goes to the beach on Sunday?", null, null, "我和家人: she and her family.",
                                        2, text("Her classmates"), text("Only her"), text("She and her family")))));
    }

    // ===== Builders =====

    private static SetSpec set(PracticeType type, String title, LearningStage stage, int hsk, int xp, List<PracticeItemData> items) {
        return new SetSpec(type, new PracticeSetRequest(title, stage, hsk, xp, items, List.of(), null));
    }

    private static SetSpec write(String title, LearningStage stage, String characters, String pinyin, String meaning, int strokes) {
        PracticeItemData item = new PracticeItemData(null, characters, pinyin, meaning, null, null, null, null, List.of(),
                null, null, null);
        return new SetSpec(PracticeType.TRACING, new PracticeSetRequest(title, stage, 1, 10, List.of(item), List.of(), strokes));
    }

    private static SetSpec story(String title, LearningStage stage, int hsk, List<StoryLine> lines, List<PracticeItemData> questions) {
        return new SetSpec(PracticeType.STORY, new PracticeSetRequest(title, stage, hsk, 20, questions, lines, null));
    }

    private static PracticeItemData card(String hanzi, String pinyin, String meaning, String example, String exampleMeaning) {
        return new PracticeItemData(null, hanzi, pinyin, meaning, null, null, example, exampleMeaning, List.of(), null,
                null, null);
    }

    private static PracticeItemData quiz(String prompt, String hanzi, String pinyin, String explanation, int correct,
            ChoiceOption... options) {
        return new PracticeItemData(null, hanzi, pinyin, null, prompt, null, null, null, List.of(options), correct,
                explanation, null);
    }

    private static PracticeItemData listen(String prompt, String audio, String explanation, int correct,
            ChoiceOption... options) {
        return new PracticeItemData(null, null, null, null, prompt, audio, null, null, List.of(options), correct,
                explanation, null);
    }

    private static PracticeItemData sentence(String tiles, String pinyin, String meaning, String distractors) {
        return new PracticeItemData(null, tiles, pinyin, meaning, null, null, null, null, List.of(), null, null, distractors);
    }

    private static PracticeItemData pair(String hanzi, String pinyin, String meaning) {
        return new PracticeItemData(null, hanzi, pinyin, meaning, null, null, null, null, List.of(), null, null, null);
    }

    private static StoryLine line(String hanzi, String pinyin, String translation) {
        return new StoryLine(hanzi, pinyin, translation);
    }

    private static ChoiceOption text(String text) {
        return new ChoiceOption(text, null);
    }

    private static ChoiceOption han(String hanzi, String pinyin) {
        return new ChoiceOption(hanzi, pinyin);
    }
}
