package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.List;

/**
 * The lesson content of the demo classes: for each lesson its key phrases (also its flashcards and matching pairs)
 * and the characters to trace. Quizzes are built from the phrases ({@link #quiz}); Greetings has the quiz of the
 * prototype.
 */
final class DemoClassContent {

    private DemoClassContent() {
    }

    record Word(String chinese, String pinyin, String meaning) {
    }

    record LessonSpec(String title, String summary, int minutes, String video, int videoSeconds, List<Word> words, List<Word> trace) {
    }

    record UnitSpec(String title, List<LessonSpec> lessons) {
    }

    record Option(String text, boolean correct) {
    }

    /** One quiz question: option-based types list options; FILL_BLANK has the answer text. */
    record Question(String type, String text, List<Option> options, String answer, String explanation, String hint) {
    }

    static Word w(String chinese, String pinyin, String meaning) {
        return new Word(chinese, pinyin, meaning);
    }

    static LessonSpec lesson(String title, String summary, int minutes, String video, int videoSeconds, List<Word> words, List<Word> trace) {
        return new LessonSpec(title, summary, minutes, video, videoSeconds, words, trace);
    }

    // ----- Li Wei: Mandarin for Beginners -----

    static final LessonSpec GREETINGS = lesson("Greetings", "Say hello, ask how someone is and say goodbye.", 18,
            "How to say hello", 375,
            List.of(w("你好", "nǐ hǎo", "Hello"), w("你好吗？", "nǐ hǎo ma?", "How are you?"),
                    w("早上好", "zǎoshang hǎo", "Good morning"), w("谢谢", "xièxie", "Thank you"),
                    w("再见", "zàijiàn", "Goodbye")),
            List.of(w("你", "nǐ", "you"), w("好", "hǎo", "good")));

    static final LessonSpec NUMBERS = lesson("Numbers 1–10", "Count from one to ten and say a number you hear.", 15,
            "Counting from one to ten", 320,
            List.of(w("一", "yī", "one"), w("二", "èr", "two"), w("三", "sān", "three"), w("五", "wǔ", "five"),
                    w("十", "shí", "ten")),
            List.of(w("一", "yī", "one"), w("二", "èr", "two"), w("三", "sān", "three")));

    static final LessonSpec UNIVERSITY_LIFE = lesson("University Life", "Talk about places on campus and your daily routine.", 14,
            "Around the campus", 340,
            List.of(w("大学生", "dàxuéshēng", "university student"), w("图书馆", "túshūguǎn", "library"),
                    w("食堂", "shítáng", "canteen"), w("每天", "měitiān", "every day")),
            List.of(w("学", "xué", "study"), w("生", "shēng", "student")));

    static final LessonSpec ORDERING_FOOD = lesson("Ordering Food at the Canteen", "Order a meal, ask the price and say it tastes good.", 16,
            "At the canteen", 410,
            List.of(w("点菜", "diǎn cài", "to order food"), w("多少钱？", "duōshao qián?", "How much?"),
                    w("好吃", "hǎochī", "delicious"), w("米饭", "mǐfàn", "rice"), w("一杯茶", "yì bēi chá", "a cup of tea")),
            List.of(w("吃", "chī", "eat"), w("饭", "fàn", "rice, meal")));

    static final LessonSpec FINDING_PLACES = lesson("Finding Places", "Ask where a place is and understand left and right.", 14,
            "Where is the library?", 300,
            List.of(w("在哪儿？", "zài nǎr?", "Where is it?"), w("左边", "zuǒbian", "left side"),
                    w("右边", "yòubian", "right side"), w("教室", "jiàoshì", "classroom"), w("宿舍", "sùshè", "dormitory")),
            List.of(w("左", "zuǒ", "left"), w("右", "yòu", "right")));

    static final List<UnitSpec> MANDARIN_FOR_BEGINNERS = List.of(
            new UnitSpec("First Conversations", List.of(GREETINGS, NUMBERS, UNIVERSITY_LIFE)),
            new UnitSpec("Campus Life", List.of(ORDERING_FOOD, FINDING_PLACES)));

    /** The Greetings quiz of the prototype (Question 1 of 5: How do you politely reply to "你好吗？"). */
    static final List<Question> GREETINGS_QUIZ = List.of(
            new Question("MULTIPLE_CHOICE", "How do you politely reply to “你好吗？” (How are you?)",
                    List.of(new Option("我很好。", false), new Option("我很好，谢谢！", true),
                            new Option("再见！", false), new Option("你叫什么名字？", false)),
                    null, "我很好，谢谢！ (wǒ hěn hǎo, xièxie) means “I'm fine, thanks!”, the polite reply.",
                    "很好 means “very good”. Add 谢谢 to thank them."),
            new Question("CHOOSE_PINYIN", "Choose the pinyin for 再见.",
                    List.of(new Option("zàijiàn", true), new Option("xièxie", false), new Option("zǎoshang", false), new Option("nǐ hǎo", false)),
                    null, "再见 is zàijiàn, “goodbye”.", "It starts with z and has two fourth tones."),
            new Question("TRUE_FALSE", "谢谢 means “thank you”.",
                    List.of(new Option("True", true), new Option("False", false)),
                    null, "谢谢 (xièxie) is “thank you”.", null),
            new Question("MULTIPLE_CHOICE", "What does 你好 mean?",
                    List.of(new Option("Goodbye", false), new Option("Hello", true), new Option("Thank you", false), new Option("Good morning", false)),
                    null, "你好 (nǐ hǎo) is the everyday “hello”.", "You say it when you meet someone."),
            new Question("MULTIPLE_CHOICE", "Which greeting do you use in the morning?",
                    List.of(new Option("晚上好", false), new Option("早上好", true), new Option("下午好", false), new Option("你好吗", false)),
                    null, "早 means early, so 早上好 is “good morning”.", "Look for 早, “early”."));

    // ----- Li Wei: HSK 1 Prep -----

    static final List<UnitSpec> HSK1_PREP = List.of(
            new UnitSpec("People and Places", List.of(
                    lesson("Family Words", "Name the people in your family.", 12, "My family", 280,
                            List.of(w("爸爸", "bàba", "dad"), w("妈妈", "māma", "mum"), w("哥哥", "gēge", "older brother"),
                                    w("妹妹", "mèimei", "younger sister")),
                            List.of(w("爸", "bà", "dad"), w("妈", "mā", "mum"))),
                    lesson("Countries and Languages", "Say where you are from and which languages you speak.", 12,
                            "Where are you from?", 300,
                            List.of(w("中国", "Zhōngguó", "China"), w("马来西亚", "Mǎláixīyà", "Malaysia"),
                                    w("汉语", "Hànyǔ", "Chinese language"), w("英语", "Yīngyǔ", "English")),
                            List.of(w("中", "zhōng", "middle"), w("国", "guó", "country"))),
                    lesson("Time Words", "Talk about today, tomorrow and yesterday.", 12, "Today and tomorrow", 260,
                            List.of(w("今天", "jīntiān", "today"), w("明天", "míngtiān", "tomorrow"),
                                    w("昨天", "zuótiān", "yesterday"), w("现在", "xiànzài", "now")),
                            List.of(w("天", "tiān", "day"), w("今", "jīn", "today"))))),
            new UnitSpec("Daily Actions", List.of(
                    lesson("Verbs You Use Every Day", "Eat, drink, watch and go: the verbs of every day.", 12, "Everyday verbs", 290,
                            List.of(w("吃", "chī", "eat"), w("喝", "hē", "drink"), w("看", "kàn", "look, watch"), w("去", "qù", "go")),
                            List.of(w("看", "kàn", "look"), w("去", "qù", "go"))),
                    lesson("Shopping Words", "Buy things and talk about money.", 12, "At the shop", 270,
                            List.of(w("买", "mǎi", "buy"), w("钱", "qián", "money"), w("东西", "dōngxi", "things"),
                                    w("商店", "shāngdiàn", "shop")),
                            List.of(w("买", "mǎi", "buy"), w("钱", "qián", "money"))),
                    lesson("Weather and Seasons", "Say if it is hot, cold or raining.", 12, "What's the weather like?", 250,
                            List.of(w("热", "rè", "hot"), w("冷", "lěng", "cold"), w("下雨", "xià yǔ", "to rain"),
                                    w("天气", "tiānqì", "weather")),
                            List.of(w("热", "rè", "hot"), w("冷", "lěng", "cold"))))));

    // ----- Li Wei: Chinese Culture and Festivals (in review) and Campus Mandarin (draft) -----

    static final LessonSpec LANTERN_FESTIVAL = lesson("Lantern Festival", "Words and customs of the Lantern Festival.", 15,
            "The lantern walk", 330,
            List.of(w("灯笼", "dēnglong", "lantern"), w("红色", "hóngsè", "red"), w("节日", "jiérì", "festival"),
                    w("月亮", "yuèliang", "moon")),
            List.of(w("灯", "dēng", "lamp"), w("节", "jié", "festival")));

    static final LessonSpec FESTIVAL_GREETINGS = lesson("Festival Greetings", "Wish people well at Chinese New Year.", 12,
            null, 0,
            List.of(w("新年快乐", "xīnnián kuàilè", "Happy New Year"), w("恭喜发财", "gōngxǐ fācái", "wishing you prosperity"),
                    w("红包", "hóngbāo", "red packet")),
            List.of());

    static final LessonSpec AT_THE_LIBRARY = lesson("At the Library", "Borrow and return books in Mandarin.", 12, null, 0,
            List.of(w("借书", "jiè shū", "borrow books"), w("还书", "huán shū", "return books"), w("安静", "ānjìng", "quiet")),
            List.of());

    // ----- other teachers -----

    static final List<UnitSpec> EVERYDAY_CONVERSATIONS = List.of(
            new UnitSpec("Meeting People", List.of(
                    lesson("Meeting New People", "Introduce yourself and ask someone's name.", 15, "Nice to meet you", 360,
                            List.of(w("认识你很高兴", "rènshi nǐ hěn gāoxìng", "nice to meet you"),
                                    w("你叫什么名字？", "nǐ jiào shénme míngzi?", "What's your name?"),
                                    w("我是马来西亚人", "wǒ shì Mǎláixīyà rén", "I'm Malaysian"), w("朋友", "péngyou", "friend")),
                            List.of(w("朋", "péng", "friend"), w("友", "yǒu", "friend"))),
                    lesson("Ordering at a Hawker Stall", "Order noodles the way you like them.", 15, "At the hawker stall", 390,
                            List.of(w("老板", "lǎobǎn", "stall owner"), w("来一碗", "lái yì wǎn", "one bowl, please"),
                                    w("打包", "dǎbāo", "take away"), w("不要辣", "bú yào là", "not spicy")),
                            List.of(w("碗", "wǎn", "bowl"), w("辣", "là", "spicy"))))),
            new UnitSpec("Out and About", List.of(
                    lesson("Shopping at the Pasar Malam", "Ask the price and bargain politely.", 15, "At the night market", 350,
                            List.of(w("便宜一点", "piányi yìdiǎn", "a bit cheaper"), w("太贵了", "tài guì le", "too expensive"),
                                    w("这个", "zhège", "this one"), w("多少钱", "duōshao qián", "how much")),
                            List.of(w("贵", "guì", "expensive"), w("买", "mǎi", "buy"))),
                    lesson("Taking the LRT", "Find the station and change trains.", 15, "On the LRT", 310,
                            List.of(w("车站", "chēzhàn", "station"), w("上车", "shàng chē", "get on"),
                                    w("下车", "xià chē", "get off"), w("换车", "huàn chē", "change trains")),
                            List.of(w("车", "chē", "vehicle"), w("站", "zhàn", "station"))))));

    static final List<UnitSpec> PINYIN = List.of(
            new UnitSpec("Tones", List.of(
                    lesson("The Four Tones", "Hear and say the four tones of Mandarin.", 12, "The four tones", 300,
                            List.of(w("妈", "mā", "mum"), w("麻", "má", "hemp"), w("马", "mǎ", "horse"), w("骂", "mà", "scold")),
                            List.of(w("马", "mǎ", "horse"), w("妈", "mā", "mum"))),
                    lesson("Tone Changes", "When 你, 不 and 一 change their tone.", 12, "Tone changes", 280,
                            List.of(w("你好", "nǐ hǎo", "hello"), w("不是", "bú shì", "is not"), w("一个", "yí ge", "one (item)"),
                                    w("很好", "hěn hǎo", "very good")),
                            List.of(w("不", "bù", "not"), w("是", "shì", "to be"))))),
            new UnitSpec("Spelling", List.of(
                    lesson("Initials b p m f", "Spell words that start with b, p, m and f.", 12, "b p m f", 260,
                            List.of(w("爸爸", "bàba", "dad"), w("朋友", "péngyou", "friend"), w("妈妈", "māma", "mum"),
                                    w("飞机", "fēijī", "plane")),
                            List.of(w("飞", "fēi", "fly"), w("机", "jī", "machine"))),
                    lesson("Finals a o e", "Read the finals a, o and e.", 12, "a o e", 250,
                            List.of(w("啊", "a", "ah"), w("我", "wǒ", "I, me"), w("饿", "è", "hungry"), w("哥哥", "gēge", "older brother")),
                            List.of(w("我", "wǒ", "I"), w("饿", "è", "hungry"))))));

    static final List<UnitSpec> CHARACTERS = List.of(
            new UnitSpec("Strokes", List.of(
                    lesson("Basic Strokes", "Write your first characters stroke by stroke.", 12, "The basic strokes", 290,
                            List.of(w("一", "yī", "one"), w("十", "shí", "ten"), w("人", "rén", "person"), w("大", "dà", "big")),
                            List.of(w("人", "rén", "person"), w("大", "dà", "big"))),
                    lesson("Stroke Order", "Top to bottom, left to right.", 12, "Stroke order rules", 270,
                            List.of(w("口", "kǒu", "mouth"), w("日", "rì", "sun"), w("月", "yuè", "moon"), w("山", "shān", "mountain")),
                            List.of(w("日", "rì", "sun"), w("月", "yuè", "moon"))))),
            new UnitSpec("Radicals", List.of(
                    lesson("Water and Person Radicals", "Spot the water and person radicals.", 12, "Radicals that help", 300,
                            List.of(w("河", "hé", "river"), w("喝", "hē", "drink"), w("你", "nǐ", "you"), w("他", "tā", "he")),
                            List.of(w("河", "hé", "river"), w("他", "tā", "he"))),
                    lesson("Hand and Heart Radicals", "Characters about doing and feeling.", 12, "Hand and heart", 280,
                            List.of(w("打", "dǎ", "hit, play"), w("拿", "ná", "take"), w("想", "xiǎng", "think"), w("忙", "máng", "busy")),
                            List.of(w("想", "xiǎng", "think"), w("忙", "máng", "busy"))))));

    static final List<UnitSpec> TRAVEL = List.of(
            new UnitSpec("At the Airport", List.of(
                    lesson("Checking In", "Check in for a flight.", 15, "At the airport", 330,
                            List.of(w("护照", "hùzhào", "passport"), w("行李", "xíngli", "luggage"),
                                    w("登机牌", "dēngjīpái", "boarding pass"), w("机场", "jīchǎng", "airport")),
                            List.of(w("护", "hù", "protect"), w("照", "zhào", "photo"))),
                    lesson("At the Hotel", "Check in and out of a hotel.", 15, "At the hotel", 320,
                            List.of(w("酒店", "jiǔdiàn", "hotel"), w("房间", "fángjiān", "room"), w("钥匙", "yàoshi", "key"),
                                    w("退房", "tuìfáng", "check out")),
                            List.of(w("房", "fáng", "room"), w("间", "jiān", "room (measure word)"))))),
            new UnitSpec("Getting Around", List.of(
                    lesson("Taking a Taxi", "Tell the driver where to go.", 15, "In a taxi", 300,
                            List.of(w("出租车", "chūzūchē", "taxi"), w("去这里", "qù zhèlǐ", "go here"), w("停车", "tíng chē", "stop the car"),
                                    w("发票", "fāpiào", "receipt")),
                            List.of(w("停", "tíng", "stop"), w("去", "qù", "go"))),
                    lesson("Asking for Help", "Ask politely when you are lost.", 15, "Excuse me", 290,
                            List.of(w("请问", "qǐngwèn", "excuse me"), w("帮我", "bāng wǒ", "help me"), w("迷路", "mílù", "get lost"),
                                    w("洗手间", "xǐshǒujiān", "toilet")),
                            List.of(w("请", "qǐng", "please"), w("问", "wèn", "ask"))))));

    static final List<UnitSpec> AT_WORK = List.of(
            new UnitSpec("At the Office", List.of(
                    lesson("Greeting Customers", "Welcome customers and ask them to wait.", 15, "Welcome!", 310,
                            List.of(w("欢迎光临", "huānyíng guānglín", "welcome"), w("请坐", "qǐng zuò", "please sit"),
                                    w("稍等", "shāo děng", "one moment"), w("不客气", "bú kèqi", "you're welcome")),
                            List.of(w("请", "qǐng", "please"), w("坐", "zuò", "sit"))),
                    lesson("Small Talk", "Chat about the weekend with a colleague.", 15, "How was your weekend?", 300,
                            List.of(w("最近怎么样？", "zuìjìn zěnmeyàng?", "How have you been?"), w("周末", "zhōumò", "weekend"),
                                    w("忙", "máng", "busy"), w("休息", "xiūxi", "rest")),
                            List.of(w("忙", "máng", "busy"), w("休", "xiū", "rest"))))),
            new UnitSpec("Calls and Messages", List.of(
                    lesson("On the Phone", "Answer the phone and take a number.", 15, "Hello, who's speaking?", 280,
                            List.of(w("喂", "wéi", "hello (on the phone)"), w("请稍等", "qǐng shāo děng", "please hold"),
                                    w("打电话", "dǎ diànhuà", "make a call"), w("号码", "hàomǎ", "number")),
                            List.of(w("电", "diàn", "electricity"), w("话", "huà", "speech"))),
                    lesson("Meetings", "Words for a short team meeting.", 15, "Team meeting", 290,
                            List.of(w("开会", "kāihuì", "have a meeting"), w("同事", "tóngshì", "colleague"), w("经理", "jīnglǐ", "manager"),
                                    w("时间", "shíjiān", "time")),
                            List.of(w("会", "huì", "meeting"), w("时", "shí", "time"))))));

    static final List<UnitSpec> NUMBERS_MONEY_TIME = List.of(
            new UnitSpec("Numbers", List.of(
                    lesson("Counting to 100", "Count in tens up to one hundred.", 12, "From 一 to 一百", 300,
                            List.of(w("二十", "èrshí", "twenty"), w("五十", "wǔshí", "fifty"), w("九十九", "jiǔshíjiǔ", "ninety-nine"),
                                    w("一百", "yìbǎi", "one hundred")),
                            List.of(w("百", "bǎi", "hundred"), w("九", "jiǔ", "nine"))),
                    lesson("Phone Numbers", "Read a phone number out loud.", 12, "Phone numbers", 240,
                            List.of(w("零", "líng", "zero"), w("幺", "yāo", "one (in phone numbers)"), w("号码", "hàomǎ", "number"),
                                    w("几", "jǐ", "how many")),
                            List.of(w("零", "líng", "zero"), w("几", "jǐ", "how many"))))),
            new UnitSpec("Money and Time", List.of(
                    lesson("Paying in Ringgit", "Pay, count and get your change.", 12, "Paying at the counter", 270,
                            List.of(w("块", "kuài", "ringgit (dollar)"), w("毛", "máo", "ten sen"), w("一共", "yígòng", "altogether"),
                                    w("找钱", "zhǎo qián", "give change")),
                            List.of(w("块", "kuài", "dollar"), w("共", "gòng", "together"))),
                    lesson("Telling the Time", "Say what time it is.", 12, "What time is it?", 260,
                            List.of(w("点", "diǎn", "o'clock"), w("半", "bàn", "half"), w("分钟", "fēnzhōng", "minute"),
                                    w("几点", "jǐ diǎn", "what time")),
                            List.of(w("点", "diǎn", "o'clock"), w("半", "bàn", "half"))))));

    /**
     * Five questions from the lesson phrases (2 marks each): a meaning, the pinyin, true or false, the word for a
     * meaning and typing the word. Options rotate so the right one is not always first.
     */
    static List<Question> quiz(LessonSpec spec) {
        List<Word> words = spec.words();
        Word first = words.get(0);
        Word second = words.get(1 % words.size());
        Word third = words.get(2 % words.size());
        Word fourth = words.get(3 % words.size());
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("MULTIPLE_CHOICE", "What does " + first.chinese() + " mean?",
                rotate(words.stream().map(word -> new Option(word.meaning(), word == first)).toList(), 1), null,
                first.chinese() + " (" + first.pinyin() + ") means “" + first.meaning() + "”.",
                "Say it aloud: " + first.pinyin() + "."));
        questions.add(new Question("CHOOSE_PINYIN", "Choose the pinyin for " + second.chinese() + ".",
                rotate(words.stream().map(word -> new Option(word.pinyin(), word == second)).toList(), 2), null,
                second.chinese() + " is " + second.pinyin() + ", “" + second.meaning() + "”.",
                "It means “" + second.meaning() + "”."));
        questions.add(new Question("TRUE_FALSE", third.chinese() + " means “" + third.meaning() + "”.",
                List.of(new Option("True", true), new Option("False", false)), null,
                third.chinese() + " (" + third.pinyin() + ") is “" + third.meaning() + "”.", null));
        questions.add(new Question("CHARACTER_MEANING", "Which word means “" + fourth.meaning() + "”?",
                rotate(words.stream().map(word -> new Option(word.chinese(), word == fourth)).toList(), 3), null,
                "“" + fourth.meaning() + "” is " + fourth.chinese() + " (" + fourth.pinyin() + ").",
                "Its pinyin is " + fourth.pinyin() + "."));
        questions.add(new Question("FILL_BLANK", "Type the Chinese for “" + first.meaning() + "”.", List.of(),
                first.chinese(), "“" + first.meaning() + "” is " + first.chinese() + " (" + first.pinyin() + ").",
                "It is the first key phrase of the lesson."));
        return questions;
    }

    private static List<Option> rotate(List<Option> options, int by) {
        List<Option> limited = new ArrayList<>(options.subList(0, Math.min(4, options.size())));
        if (limited.stream().noneMatch(Option::correct)) {
            limited.set(limited.size() - 1, options.stream().filter(Option::correct).findFirst().orElseThrow());
        }
        List<Option> rotated = new ArrayList<>();
        for (int index = 0; index < limited.size(); index++) {
            rotated.add(limited.get((index + by) % limited.size()));
        }
        return rotated;
    }
}
