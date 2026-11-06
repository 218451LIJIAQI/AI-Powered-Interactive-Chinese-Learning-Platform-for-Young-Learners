package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.List;

/**
 * The lesson content of the demo classes, following the lesson rule of the prototype: one short video, 3 to 5 key
 * phrases with at least 2 usable sentences (also the flashcards), exactly three activities in the order Flashcards,
 * Tracing and Matching, and a 5-question quiz (2 word meanings, 1 situation, 1 sentence, 1 listening). Quizzes are built
 * from the lesson's words and phrases ({@link #quiz}); Meeting Classmates and University Life have the quizzes of the
 * prototype. Answers in Chinese show their pinyin on a second line.
 */
final class DemoClassContent {

    private DemoClassContent() {
    }

    record Word(String chinese, String pinyin, String meaning) {
    }

    /** The situation question: what the student is in, and which key phrase is the one clearly best reply. */
    record Situation(String prompt, int reply, String hint) {
    }

    record LessonSpec(
            String title,
            String summary,
            int minutes,
            String video,
            int videoSeconds,
            List<Word> phrases,
            List<Word> words,
            List<Word> trace,
            String flashcards,
            String matching,
            List<Word> pairs,
            Situation situation,
            List<Question> quiz) {
    }

    record UnitSpec(String title, List<LessonSpec> lessons) {
    }

    record Option(String text, boolean correct) {
    }

    /** One quiz question; {@code spoken} is what a listen-and-choose question reads aloud (else null). */
    record Question(String type, String text, List<Option> options, String explanation, String hint, String spoken) {
    }

    static Word w(String chinese, String pinyin, String meaning) {
        return new Word(chinese, pinyin, meaning);
    }

    private static Situation when(String prompt, int reply, String hint) {
        return new Situation(prompt, reply, hint);
    }

    /** A lesson whose quiz is built from its words and phrases and whose matching pairs are its words. */
    private static LessonSpec lesson(String title, String summary, String video, int videoSeconds, List<Word> phrases,
            List<Word> words, List<Word> trace, String flashcards, String matching, Situation situation) {
        return new LessonSpec(title, summary, 15, video, videoSeconds, phrases, words, trace, flashcards, matching, words,
                situation, List.of());
    }

    /** A lesson the teacher has only just started: key phrases, no video, activities or quiz yet. */
    private static LessonSpec draft(String title, String summary, List<Word> phrases) {
        return new LessonSpec(title, summary, 12, null, 0, phrases, List.of(), List.of(), null, null, List.of(), null, List.of());
    }

    // ----- Li Wei: Mandarin for Campus Life -----

    static final LessonSpec MEETING_CLASSMATES = new LessonSpec("Meeting Classmates",
            "Introduce yourself and ask a classmate's name.", 18, "Meeting a classmate", 375,
            List.of(w("你叫什么名字？", "nǐ jiào shénme míngzi?", "What's your name?"),
                    w("我叫 Amira。", "wǒ jiào Amira.", "My name is Amira."),
                    w("你是学生吗？", "nǐ shì xuésheng ma?", "Are you a student?"),
                    w("我是大学生。", "wǒ shì dàxuéshēng.", "I'm a university student."),
                    w("认识你很高兴。", "rènshi nǐ hěn gāoxìng.", "Nice to meet you.")),
            List.of(),
            List.of(w("名", "míng", "name"), w("字", "zì", "character, word")),
            "Classmate Phrases", "Match the Answers",
            List.of(w("你叫什么名字？", "nǐ jiào shénme míngzi?", "我叫 Amira。"),
                    w("你是老师吗？", "nǐ shì lǎoshī ma?", "不是，我是学生。"),
                    w("你是学生吗？", "nǐ shì xuésheng ma?", "是，我是大学生。"),
                    w("谢谢！", "xièxie!", "不客气！")),
            null,
            List.of(
                    new Question("MULTIPLE_CHOICE", "Reply to “你叫什么名字？” (“What's your name?”)",
                            List.of(zh("我是学生。", "wǒ shì xuésheng.", false), zh("我叫 Amira。", "wǒ jiào Amira.", true),
                                    zh("再见！", "zàijiàn!", false), zh("谢谢！", "xièxie!", false)),
                            "我叫 Amira。 (wǒ jiào Amira.) means “My name is Amira.”, the reply to 你叫什么名字？",
                            "叫 means “to be called”. Say 我叫 + your name.", null),
                    new Question("MULTIPLE_CHOICE", "What does 学生 mean?",
                            List.of(new Option("Teacher", false), new Option("Classmate", false), new Option("Student", true),
                                    new Option("Friend", false)),
                            "学生 (xuésheng) means “student”.", "老师 is a teacher. Who learns from a teacher?", null),
                    new Question("MULTIPLE_CHOICE", "What does 认识你很高兴。 (rènshi nǐ hěn gāoxìng.) mean?",
                            List.of(new Option("Nice to meet you.", true), new Option("My name is Amira.", false),
                                    new Option("Are you a student?", false), new Option("I'm a university student.", false)),
                            "认识 is to get to know and 高兴 is happy: “Nice to meet you.”", null, null),
                    new Question("MULTIPLE_CHOICE", "What does 名字 mean?",
                            List.of(new Option("Student", false), new Option("Name", true), new Option("Teacher", false),
                                    new Option("Friend", false)),
                            "名字 (míngzi) means “name”, as in 你叫什么名字？", "You ask for it with 你叫什么……？", null),
                    new Question("LISTEN_CHOOSE", "Listen and choose: which phrase did you hear?",
                            List.of(zh("你是老师吗？", "nǐ shì lǎoshī ma?", false), zh("你是学生吗？", "nǐ shì xuésheng ma?", true),
                                    zh("你是中国人吗？", "nǐ shì Zhōngguórén ma?", false), zh("你叫什么名字？", "nǐ jiào shénme míngzi?", false)),
                            "学生 (xuésheng) is “student”. 老师 (lǎoshī) is “teacher”.", null, "你是学生吗？")));

    static final LessonSpec ROOM_NUMBERS = lesson("Room Numbers", "Say room numbers and ask where a room is.",
            "Which room?", 320,
            List.of(w("你住几号房？", "nǐ zhù jǐ hào fáng?", "What room are you in?"),
                    w("我住三零五。", "wǒ zhù sān líng wǔ.", "I'm in room 305."),
                    w("教室在二楼。", "jiàoshì zài èr lóu.", "The classroom is on the second floor.")),
            List.of(w("房间", "fángjiān", "room"), w("楼", "lóu", "floor, building"), w("零", "líng", "zero"),
                    w("号", "hào", "number")),
            List.of(w("房", "fáng", "room"), w("号", "hào", "number")),
            "Room Number Phrases", "Match the Rooms",
            when("A classmate asks “你住几号房？” What do you reply?", 1, "几号 asks for a number. Say 我住 + your room number."));

    static final LessonSpec UNIVERSITY_LIFE = new LessonSpec("University Life",
            "Talk about places on campus and your daily routine.", 14, "Around the campus", 340,
            List.of(w("我是大学生。", "wǒ shì dàxuéshēng.", "I'm a university student."),
                    w("图书馆在哪儿？", "túshūguǎn zài nǎr?", "Where is the library?"),
                    w("我去食堂吃饭。", "wǒ qù shítáng chīfàn.", "I'm going to the canteen to eat."),
                    w("我每天去学校。", "wǒ měitiān qù xuéxiào.", "I go to school every day.")),
            List.of(),
            List.of(w("学", "xué", "to study"), w("生", "shēng", "student")),
            "University Life Phrases", "Campus Places",
            List.of(w("图书馆", "túshūguǎn", "library"), w("食堂", "shítáng", "canteen"), w("教室", "jiàoshì", "classroom"),
                    w("宿舍", "sùshè", "dormitory")),
            null,
            List.of(
                    new Question("MULTIPLE_CHOICE", "What does 图书馆 mean?",
                            List.of(new Option("Library", true), new Option("Canteen", false), new Option("Classroom", false),
                                    new Option("Dormitory", false)),
                            "图书馆 (túshūguǎn) is where you borrow and read books.", "It is the place where you borrow books.", null),
                    new Question("MULTIPLE_CHOICE", "What does 食堂 mean?",
                            List.of(new Option("Library", false), new Option("Canteen", true), new Option("Classroom", false),
                                    new Option("Bookshop", false)),
                            "食堂 (shítáng) is the canteen where students eat.", null, null),
                    new Question("MULTIPLE_CHOICE", "You want to find the library. What do you ask?",
                            List.of(zh("图书馆很好。", "túshūguǎn hěn hǎo.", false), zh("我是大学生。", "wǒ shì dàxuéshēng.", false),
                                    zh("图书馆在哪儿？", "túshūguǎn zài nǎr?", true), zh("我去食堂。", "wǒ qù shítáng.", false)),
                            "哪儿 (nǎr) means “where”, so 图书馆在哪儿？ asks where the library is.", null, null),
                    new Question("MULTIPLE_CHOICE", "Complete: 我每天去___看书。 (I go to the library every day to read.)",
                            List.of(zh("食堂", "shítáng", false), zh("教室", "jiàoshì", false), zh("宿舍", "sùshè", false),
                                    zh("图书馆", "túshūguǎn", true)),
                            "You read books in the library, 图书馆.", null, null),
                    new Question("LISTEN_CHOOSE", "Listen: which word do you hear?",
                            List.of(zh("明天", "míngtiān", false), zh("每天", "měitiān", true), zh("今天", "jīntiān", false),
                                    zh("每周", "měi zhōu", false)),
                            "每天 (měitiān) means every day.", null, "每天")));

    static final LessonSpec ORDERING_FOOD = new LessonSpec("Ordering Food at the Canteen",
            "Order a meal, ask the price and say it tastes good.", 16, "At the canteen", 410,
            List.of(w("我要一碗米饭。", "wǒ yào yì wǎn mǐfàn.", "I'd like a bowl of rice."),
                    w("多少钱？", "duōshao qián?", "How much is it?"),
                    w("很好吃！", "hěn hǎochī!", "It's delicious!")),
            List.of(w("米饭", "mǐfàn", "rice"), w("块", "kuài", "ringgit, dollar"), w("菜单", "càidān", "menu"),
                    w("好吃", "hǎochī", "delicious")),
            List.of(w("米", "mǐ", "rice"), w("饭", "fàn", "meal, cooked rice")),
            "Mamak Menu Flashcards", "Match the Prices",
            List.of(w("五块", "wǔ kuài", "5 ringgit"), w("八块", "bā kuài", "8 ringgit"), w("十块", "shí kuài", "10 ringgit"),
                    w("十五块", "shíwǔ kuài", "15 ringgit")),
            when("You want to know the price of your meal. What do you ask?", 1, "多少 means “how much”."),
            List.of());

    static final LessonSpec FINDING_PLACES = lesson("Finding Places", "Ask where a place is and understand left and right.",
            "Where is the library?", 300,
            List.of(w("请问，图书馆在哪儿？", "qǐngwèn, túshūguǎn zài nǎr?", "Excuse me, where is the library?"),
                    w("在左边。", "zài zuǒbian.", "It's on the left."),
                    w("在右边。", "zài yòubian.", "It's on the right."),
                    w("教室在二楼。", "jiàoshì zài èr lóu.", "The classroom is on the second floor.")),
            List.of(w("左边", "zuǒbian", "left side"), w("右边", "yòubian", "right side"), w("教室", "jiàoshì", "classroom"),
                    w("宿舍", "sùshè", "dormitory")),
            List.of(w("左", "zuǒ", "left"), w("右", "yòu", "right")),
            "Finding Places Phrases", "Places and Directions",
            when("You can't find the library. What do you ask?", 0, "请问 is a polite “excuse me”."));

    static final List<UnitSpec> CAMPUS_LIFE = List.of(
            new UnitSpec("First Conversations", List.of(MEETING_CLASSMATES, ROOM_NUMBERS, UNIVERSITY_LIFE)),
            new UnitSpec("Campus Life", List.of(ORDERING_FOOD, FINDING_PLACES)));

    // ----- Li Wei: Chinese Through Film -----

    static final List<UnitSpec> THROUGH_FILM = List.of(
            new UnitSpec("Everyday Lines", List.of(
                    lesson("At the Bus Stop", "Lines you hear while waiting for the bus.", "Scene: the last bus", 300,
                            List.of(w("车来了！", "chē lái le!", "The bus is here!"), w("你去哪儿？", "nǐ qù nǎr?", "Where are you going?"),
                                    w("我要回家。", "wǒ yào huí jiā.", "I'm going home.")),
                            List.of(w("车", "chē", "vehicle"), w("回家", "huí jiā", "to go home"), w("等", "děng", "to wait"),
                                    w("来", "lái", "to come")),
                            List.of(w("车", "chē", "vehicle"), w("回", "huí", "to go back")),
                            "Bus Stop Lines", "Match the Lines",
                            when("A friend at the bus stop asks “你去哪儿？” What do you reply?", 2, "回家 is to go home.")),
                    lesson("Morning Coffee", "Order a coffee and say how your day went.", "Scene: the coffee stall", 290,
                            List.of(w("早上好！", "zǎoshang hǎo!", "Good morning!"), w("我要一杯咖啡。", "wǒ yào yì bēi kāfēi.", "I'd like a cup of coffee."),
                                    w("昨天我很忙。", "zuótiān wǒ hěn máng.", "I was busy yesterday.")),
                            List.of(w("咖啡", "kāfēi", "coffee"), w("杯", "bēi", "cup of"), w("昨天", "zuótiān", "yesterday"),
                                    w("早上", "zǎoshang", "morning")),
                            List.of(w("早", "zǎo", "early"), w("杯", "bēi", "cup")),
                            "Coffee Stall Lines", "Match the Morning Words",
                            when("You are ordering at a coffee stall. What do you say?", 1, "我要 is “I'd like”.")),
                    lesson("Late for Class", "Say sorry and arrange to meet later.", "Scene: running late", 280,
                            List.of(w("对不起，我来晚了！", "duìbuqǐ, wǒ lái wǎn le!", "Sorry, I'm late!"), w("没关系。", "méi guānxi.", "It's OK."),
                                    w("下课以后见！", "xiàkè yǐhòu jiàn!", "See you after class!")),
                            List.of(w("晚", "wǎn", "late"), w("下课", "xiàkè", "to finish class"), w("以后", "yǐhòu", "after"),
                                    w("见", "jiàn", "to see, to meet")),
                            List.of(w("晚", "wǎn", "late"), w("见", "jiàn", "to see")),
                            "Running Late Lines", "Match the Class Words",
                            when("You arrive after the class has started. What do you say?", 0, "对不起 is “sorry”.")))),
            new UnitSpec("Quick Reactions", List.of(
                    lesson("Good News", "React to good news like a friend would.", "Scene: exam results", 270,
                            List.of(w("太好了！", "tài hǎo le!", "That's great!"), w("我考试过了！", "wǒ kǎoshì guò le!", "I passed the exam!"),
                                    w("真的吗？", "zhēn de ma?", "Really?")),
                            List.of(w("考试", "kǎoshì", "exam"), w("真的", "zhēn de", "really"), w("高兴", "gāoxìng", "happy"),
                                    w("朋友", "péngyou", "friend")),
                            List.of(w("真", "zhēn", "real"), w("好", "hǎo", "good")),
                            "Good News Lines", "Match the Happy Words",
                            when("You passed your exam. What do you tell your friend?", 1, "考试 is an exam and 过了 is passed.")),
                    lesson("A Bad Day", "Ask what is wrong and say how you feel.", "Scene: a rainy evening", 280,
                            List.of(w("我今天很累。", "wǒ jīntiān hěn lèi.", "I'm tired today."), w("怎么了？", "zěnme le?", "What's wrong?"),
                                    w("下雨了。", "xià yǔ le.", "It's raining.")),
                            List.of(w("累", "lèi", "tired"), w("下雨", "xià yǔ", "to rain"), w("今天", "jīntiān", "today"),
                                    w("怎么了", "zěnme le", "what's wrong")),
                            List.of(w("累", "lèi", "tired"), w("雨", "yǔ", "rain")),
                            "Bad Day Lines", "Match the Weather Words",
                            when("Your friend looks sad. What do you ask?", 1, "怎么了 asks what happened.")),
                    lesson("Saying Goodbye", "End a chat and ask a friend to call you.", "Scene: at the station", 290,
                            List.of(w("我走了。", "wǒ zǒu le.", "I'm off."), w("明天见！", "míngtiān jiàn!", "See you tomorrow!"),
                                    w("回家以后给我打电话。", "huí jiā yǐhòu gěi wǒ dǎ diànhuà.", "Call me after you get home.")),
                            List.of(w("走", "zǒu", "to leave"), w("明天", "míngtiān", "tomorrow"), w("打电话", "dǎ diànhuà", "to make a call"),
                                    w("再见", "zàijiàn", "goodbye")),
                            List.of(w("走", "zǒu", "to walk"), w("明", "míng", "bright")),
                            "Goodbye Lines", "Match the Goodbye Words",
                            when("You will see your friend again tomorrow. What do you say?", 1, "明天 is tomorrow.")))));

    // ----- Li Wei: Chinese Culture and Festivals (in review) and Study Group Mandarin (draft) -----

    static final LessonSpec LANTERN_FESTIVAL = new LessonSpec("Lantern Festival", "Words and customs of the Lantern Festival.", 15,
            "The lantern walk", 330,
            List.of(w("元宵节快乐！", "yuánxiāojié kuàilè!", "Happy Lantern Festival!"), w("这是红灯笼。", "zhè shì hóng dēnglong.", "This is a red lantern."),
                    w("你吃汤圆吗？", "nǐ chī tāngyuán ma?", "Do you eat tangyuan?")),
            List.of(w("灯笼", "dēnglong", "lantern"), w("红色", "hóngsè", "red"), w("节日", "jiérì", "festival"),
                    w("汤圆", "tāngyuán", "sweet rice balls")),
            List.of(w("灯", "dēng", "lamp"), w("节", "jié", "festival")),
            "Lantern Phrases", "Match Festival Words",
            List.of(w("灯笼", "dēnglong", "lantern"), w("红色", "hóngsè", "red"), w("节日", "jiérì", "festival"),
                    w("汤圆", "tāngyuán", "sweet rice balls")),
            when("It's the Lantern Festival and you meet a friend. What do you say?", 0, "快乐 is “happy”."),
            List.of());

    static final LessonSpec FESTIVAL_GREETINGS = draft("Festival Greetings", "Wish people well at Chinese New Year.",
            List.of(w("新年快乐！", "xīnnián kuàilè!", "Happy New Year!"), w("恭喜发财！", "gōngxǐ fācái!", "Wishing you prosperity!"),
                    w("这是红包。", "zhè shì hóngbāo.", "This is a red packet.")));

    static final LessonSpec AT_THE_LIBRARY = draft("At the Library", "Borrow and return books in Mandarin.",
            List.of(w("我想借书。", "wǒ xiǎng jiè shū.", "I want to borrow a book."), w("我要还书。", "wǒ yào huán shū.", "I want to return a book."),
                    w("请安静。", "qǐng ānjìng.", "Quiet, please.")));

    // ----- Wong Siew Ling -----

    static final List<UnitSpec> EVERYDAY_CONVERSATIONS = List.of(
            new UnitSpec("Meeting People", List.of(
                    lesson("Meeting New People", "Introduce yourself and ask someone's name.", "Nice to meet you", 360,
                            List.of(w("认识你很高兴。", "rènshi nǐ hěn gāoxìng.", "Nice to meet you."), w("你叫什么名字？", "nǐ jiào shénme míngzi?", "What's your name?"),
                                    w("我是马来西亚人。", "wǒ shì Mǎláixīyàrén.", "I'm Malaysian.")),
                            List.of(w("朋友", "péngyou", "friend"), w("名字", "míngzi", "name"), w("认识", "rènshi", "to get to know"),
                                    w("高兴", "gāoxìng", "happy")),
                            List.of(w("朋", "péng", "friend"), w("友", "yǒu", "friend")),
                            "New People Phrases", "Match the People Words",
                            when("You want to say where you are from. What do you say?", 2, "人 after a country says where you are from.")),
                    lesson("Ordering at a Hawker Stall", "Order noodles the way you like them.", "At the hawker stall", 390,
                            List.of(w("老板，来一碗面。", "lǎobǎn, lái yì wǎn miàn.", "Boss, one bowl of noodles, please."), w("不要辣。", "bú yào là.", "Not spicy, please."),
                                    w("打包。", "dǎbāo.", "Take away, please.")),
                            List.of(w("老板", "lǎobǎn", "stall owner"), w("碗", "wǎn", "bowl"), w("辣", "là", "spicy"),
                                    w("打包", "dǎbāo", "to take away")),
                            List.of(w("碗", "wǎn", "bowl"), w("辣", "là", "spicy")),
                            "Hawker Stall Phrases", "Match the Hawker Words",
                            when("You can't eat spicy food. What do you tell the stall owner?", 1, "不要 is “don't want”.")))),
            new UnitSpec("Out and About", List.of(
                    lesson("Shopping at the Pasar Malam", "Ask the price and bargain politely.", "At the night market", 350,
                            List.of(w("这个多少钱？", "zhège duōshao qián?", "How much is this?"), w("太贵了！", "tài guì le!", "Too expensive!"),
                                    w("便宜一点吧。", "piányi yìdiǎn ba.", "A bit cheaper, please.")),
                            List.of(w("贵", "guì", "expensive"), w("便宜", "piányi", "cheap"), w("多少钱", "duōshao qián", "how much"),
                                    w("夜市", "yèshì", "night market")),
                            List.of(w("贵", "guì", "expensive"), w("买", "mǎi", "to buy")),
                            "Pasar Malam Phrases", "Match the Market Words",
                            when("You want the seller to lower the price. What do you say?", 2, "便宜 is “cheap”.")),
                    lesson("Taking the LRT", "Find the station and change trains.", "On the LRT", 310,
                            List.of(w("车站在哪儿？", "chēzhàn zài nǎr?", "Where is the station?"), w("我在这站下车。", "wǒ zài zhè zhàn xià chē.", "I get off at this station."),
                                    w("在哪儿换车？", "zài nǎr huàn chē?", "Where do I change trains?")),
                            List.of(w("车站", "chēzhàn", "station"), w("上车", "shàng chē", "to get on"), w("下车", "xià chē", "to get off"),
                                    w("换车", "huàn chē", "to change trains")),
                            List.of(w("车", "chē", "vehicle"), w("站", "zhàn", "station")),
                            "LRT Phrases", "Match the Train Words",
                            when("You need to find the station. What do you ask?", 0, "哪儿 is “where”.")))));

    static final List<UnitSpec> TRAVEL = List.of(
            new UnitSpec("Arriving", List.of(
                    lesson("At the Airport", "Check in for a flight and find your bags.", "At the airport", 330,
                            List.of(w("这是我的护照。", "zhè shì wǒ de hùzhào.", "This is my passport."), w("我的行李在哪儿？", "wǒ de xíngli zài nǎr?", "Where is my luggage?"),
                                    w("我去上海。", "wǒ qù Shànghǎi.", "I'm going to Shanghai.")),
                            List.of(w("护照", "hùzhào", "passport"), w("行李", "xíngli", "luggage"), w("机场", "jīchǎng", "airport"),
                                    w("飞机", "fēijī", "plane")),
                            List.of(w("机", "jī", "machine"), w("场", "chǎng", "place")),
                            "Airport Phrases", "Match the Airport Words",
                            when("You can't find your bags. What do you ask?", 1, "行李 is luggage.")),
                    lesson("At the Hotel", "Ask for a room and say how long you are staying.", "At the hotel", 320,
                            List.of(w("我要一个房间。", "wǒ yào yí ge fángjiān.", "I'd like a room."), w("我住两个晚上。", "wǒ zhù liǎng ge wǎnshang.", "I'm staying two nights."),
                                    w("钥匙在这儿。", "yàoshi zài zhèr.", "Here is the key.")),
                            List.of(w("酒店", "jiǔdiàn", "hotel"), w("房间", "fángjiān", "room"), w("钥匙", "yàoshi", "key"),
                                    w("住", "zhù", "to stay")),
                            List.of(w("房", "fáng", "room"), w("住", "zhù", "to stay")),
                            "Hotel Phrases", "Match the Hotel Words",
                            when("You arrive at a hotel without a booking. What do you say?", 0, "房间 is a room.")))),
            new UnitSpec("Around Town", List.of(
                    lesson("Taking the Metro", "Find the station and buy a ticket.", "On the metro", 300,
                            List.of(w("地铁站在哪儿？", "dìtiězhàn zài nǎr?", "Where is the metro station?"), w("我要一张票。", "wǒ yào yì zhāng piào.", "I'd like one ticket."),
                                    w("下一站是哪儿？", "xià yí zhàn shì nǎr?", "What is the next station?")),
                            List.of(w("地铁", "dìtiě", "metro"), w("票", "piào", "ticket"), w("站", "zhàn", "station"),
                                    w("张", "zhāng", "measure word for tickets")),
                            List.of(w("票", "piào", "ticket"), w("站", "zhàn", "station")),
                            "Metro Phrases", "Match the Metro Words",
                            when("You are at the ticket counter. What do you say?", 1, "票 is a ticket.")),
                    lesson("At the Restaurant", "Ask for the menu, order and pay.", "At the restaurant", 310,
                            List.of(w("请给我菜单。", "qǐng gěi wǒ càidān.", "The menu, please."), w("我要这个。", "wǒ yào zhège.", "I'd like this one."),
                                    w("买单！", "mǎidān!", "The bill, please!")),
                            List.of(w("菜单", "càidān", "menu"), w("买单", "mǎidān", "to pay the bill"), w("好吃", "hǎochī", "tasty"),
                                    w("水", "shuǐ", "water")),
                            List.of(w("菜", "cài", "dish"), w("单", "dān", "list")),
                            "Restaurant Phrases", "Match the Restaurant Words",
                            when("You have finished eating and want to pay. What do you say?", 2, "买单 is to pay the bill.")),
                    lesson("Signs and Menus", "Read the signs and menu words you meet on a trip.", "Reading signs", 280,
                            List.of(w("入口在这儿。", "rùkǒu zài zhèr.", "The entrance is here."), w("出口在哪儿？", "chūkǒu zài nǎr?", "Where is the exit?"),
                                    w("这是什么菜？", "zhè shì shénme cài?", "What dish is this?")),
                            List.of(w("入口", "rùkǒu", "entrance"), w("出口", "chūkǒu", "exit"), w("洗手间", "xǐshǒujiān", "toilet"),
                                    w("菜", "cài", "dish")),
                            List.of(w("入", "rù", "to enter"), w("出", "chū", "to go out")),
                            "Sign Phrases", "Match the Signs",
                            when("You want to leave the station. What do you ask?", 1, "出口 is the way out.")))));

    // ----- John Lim -----

    static final List<UnitSpec> SHOPPING_AND_PRICES = List.of(
            new UnitSpec("Asking Prices", List.of(
                    lesson("How Much Is It?", "Ask the price of anything you see.", "How much?", 290,
                            List.of(w("这个多少钱？", "zhège duōshao qián?", "How much is this?"), w("二十块。", "èrshí kuài.", "Twenty ringgit."),
                                    w("太贵了！", "tài guì le!", "Too expensive!")),
                            List.of(w("钱", "qián", "money"), w("块", "kuài", "ringgit, dollar"), w("多少", "duōshao", "how much"),
                                    w("东西", "dōngxi", "thing")),
                            List.of(w("钱", "qián", "money"), w("块", "kuài", "ringgit")),
                            "Price Phrases", "Match the Price Words",
                            when("You like a bag and want to know its price. What do you ask?", 0, "多少钱 asks how much.")),
                    lesson("Numbers at the Market", "Buy fruit by number and by weight.", "At the fruit stall", 270,
                            List.of(w("我要两个苹果。", "wǒ yào liǎng ge píngguǒ.", "I'd like two apples."), w("一斤多少钱？", "yì jīn duōshao qián?", "How much for half a kilo?"),
                                    w("一共三十块。", "yígòng sānshí kuài.", "Thirty ringgit altogether.")),
                            List.of(w("苹果", "píngguǒ", "apple"), w("斤", "jīn", "half a kilogram"), w("一共", "yígòng", "altogether"),
                                    w("水果", "shuǐguǒ", "fruit")),
                            List.of(w("果", "guǒ", "fruit"), w("共", "gòng", "together")),
                            "Market Phrases", "Match the Fruit Words",
                            when("You want to buy two apples. What do you say?", 0, "Two of something is 两.")),
                    lesson("Clothes and Sizes", "Ask for another size when something doesn't fit.", "In the shop", 290,
                            List.of(w("我想买一件衣服。", "wǒ xiǎng mǎi yí jiàn yīfu.", "I want to buy some clothes."), w("这件太大了。", "zhè jiàn tài dà le.", "This one is too big."),
                                    w("有小的吗？", "yǒu xiǎo de ma?", "Do you have a small one?")),
                            List.of(w("衣服", "yīfu", "clothes"), w("件", "jiàn", "measure word for clothes"), w("大", "dà", "big"),
                                    w("小", "xiǎo", "small")),
                            List.of(w("衣", "yī", "clothes"), w("件", "jiàn", "piece")),
                            "Clothes Phrases", "Match the Clothes Words",
                            when("The shirt is too big for you. What do you ask?", 2, "小 is small.")))),
            new UnitSpec("Bargaining and Paying", List.of(
                    lesson("Bargaining", "Ask for a better price and agree on one.", "Can you go lower?", 300,
                            List.of(w("太贵了！", "tài guì le!", "Too expensive!"), w("便宜一点吧。", "piányi yìdiǎn ba.", "A bit cheaper, please."),
                                    w("十块可以吗？", "shí kuài kěyǐ ma?", "Is ten ringgit OK?")),
                            List.of(w("贵", "guì", "expensive"), w("便宜", "piányi", "cheap"), w("可以", "kěyǐ", "OK, can"),
                                    w("一点", "yìdiǎn", "a little")),
                            List.of(w("贵", "guì", "expensive"), w("便", "pián", "cheap")),
                            "Bargaining Phrases", "Match the Bargaining Words",
                            when("You want to offer ten ringgit. What do you say?", 2, "可以吗 asks if something is OK.")),
                    lesson("Paying at the Counter", "Pay by card or cash and ask for a receipt.", "At the counter", 280,
                            List.of(w("可以刷卡吗？", "kěyǐ shuā kǎ ma?", "Can I pay by card?"), w("我付现金。", "wǒ fù xiànjīn.", "I'll pay in cash."),
                                    w("请给我发票。", "qǐng gěi wǒ fāpiào.", "A receipt, please.")),
                            List.of(w("刷卡", "shuā kǎ", "to pay by card"), w("现金", "xiànjīn", "cash"), w("发票", "fāpiào", "receipt"),
                                    w("找钱", "zhǎo qián", "to give change")),
                            List.of(w("付", "fù", "to pay"), w("卡", "kǎ", "card")),
                            "Paying Phrases", "Match the Paying Words",
                            when("You only have a bank card. What do you ask?", 0, "刷卡 is to pay by card.")))));

    static final List<UnitSpec> STUDY_ABROAD = List.of(
            new UnitSpec("First Week", List.of(
                    lesson("Registration Day", "Find the registration desk and show your student card.", "Registration day", 320,
                            List.of(w("我是新同学。", "wǒ shì xīn tóngxué.", "I'm a new student."), w("在哪儿注册？", "zài nǎr zhùcè?", "Where do I register?"),
                                    w("这是我的学生证。", "zhè shì wǒ de xuéshēngzhèng.", "This is my student card.")),
                            List.of(w("注册", "zhùcè", "to register"), w("学生证", "xuéshēngzhèng", "student card"), w("新", "xīn", "new"),
                                    w("同学", "tóngxué", "classmate")),
                            List.of(w("新", "xīn", "new"), w("同", "tóng", "together")),
                            "Registration Phrases", "Match the Registration Words",
                            when("The officer asks for proof that you are a student. What do you say?", 2, "学生证 is a student card.")),
                    lesson("Your Dorm", "Talk about your room and ask what it has.", "Your dorm", 300,
                            List.of(w("我住在宿舍。", "wǒ zhù zài sùshè.", "I live in the dorm."), w("我的房间很小。", "wǒ de fángjiān hěn xiǎo.", "My room is small."),
                                    w("有热水吗？", "yǒu rè shuǐ ma?", "Is there hot water?")),
                            List.of(w("宿舍", "sùshè", "dormitory"), w("房间", "fángjiān", "room"), w("热水", "rè shuǐ", "hot water"),
                                    w("钥匙", "yàoshi", "key")),
                            List.of(w("宿", "sù", "to stay overnight"), w("舍", "shè", "house")),
                            "Dorm Phrases", "Match the Dorm Words",
                            when("You want to take a hot shower. What do you ask?", 2, "热 is hot and 水 is water.")))),
            new UnitSpec("Getting Help", List.of(
                    lesson("The Library", "Borrow a book and ask when the library opens.", "In the library", 290,
                            List.of(w("我想借这本书。", "wǒ xiǎng jiè zhè běn shū.", "I want to borrow this book."), w("图书馆几点开门？", "túshūguǎn jǐ diǎn kāimén?", "What time does the library open?"),
                                    w("请安静。", "qǐng ānjìng.", "Quiet, please.")),
                            List.of(w("借", "jiè", "to borrow"), w("还", "huán", "to return"), w("安静", "ānjìng", "quiet"),
                                    w("图书馆", "túshūguǎn", "library")),
                            List.of(w("书", "shū", "book"), w("借", "jiè", "to borrow")),
                            "Library Phrases", "Match the Library Words",
                            when("You found a book to take home. What do you tell the librarian?", 0, "借 is to borrow.")),
                    lesson("Asking Classmates for Help", "Say you don't understand and ask for help.", "Can you help me?", 280,
                            List.of(w("你可以帮我吗？", "nǐ kěyǐ bāng wǒ ma?", "Can you help me?"), w("我不懂。", "wǒ bù dǒng.", "I don't understand."),
                                    w("谢谢你！", "xièxie nǐ!", "Thank you!")),
                            List.of(w("帮", "bāng", "to help"), w("懂", "dǒng", "to understand"), w("同学", "tóngxué", "classmate"),
                                    w("作业", "zuòyè", "homework")),
                            List.of(w("帮", "bāng", "to help"), w("懂", "dǒng", "to understand")),
                            "Help Phrases", "Match the Help Words",
                            when("You didn't follow what the teacher said. What do you tell your classmate?", 1, "懂 is to understand.")),
                    lesson("Talking to Your Teacher", "Ask a question after class.", "After class", 270,
                            List.of(w("老师，我有一个问题。", "lǎoshī, wǒ yǒu yí ge wèntí.", "Teacher, I have a question."), w("考试在什么时候？", "kǎoshì zài shénme shíhou?", "When is the exam?"),
                                    w("对不起，我来晚了。", "duìbuqǐ, wǒ lái wǎn le.", "Sorry, I'm late.")),
                            List.of(w("问题", "wèntí", "question"), w("考试", "kǎoshì", "exam"), w("时候", "shíhou", "time"),
                                    w("老师", "lǎoshī", "teacher")),
                            List.of(w("问", "wèn", "to ask"), w("题", "tí", "topic")),
                            "Teacher Phrases", "Match the Classroom Words",
                            when("You want to know the date of the exam. What do you ask?", 1, "什么时候 asks when.")))));

    // ----- Tan Mei Fong -----

    static final List<UnitSpec> BASIC_WORK = List.of(
            new UnitSpec("At the Office", List.of(
                    lesson("Meeting Colleagues", "Introduce yourself on your first day at work.", "First day at work", 310,
                            List.of(w("我是新同事。", "wǒ shì xīn tóngshì.", "I'm a new colleague."), w("我叫李明。", "wǒ jiào Lǐ Míng.", "My name is Li Ming."),
                                    w("这是我的经理。", "zhè shì wǒ de jīnglǐ.", "This is my manager.")),
                            List.of(w("同事", "tóngshì", "colleague"), w("经理", "jīnglǐ", "manager"), w("公司", "gōngsī", "company"),
                                    w("工作", "gōngzuò", "work")),
                            List.of(w("工", "gōng", "work"), w("作", "zuò", "to do")),
                            "Colleague Phrases", "Match the Office People",
                            when("You want to introduce your boss to a visitor. What do you say?", 2, "经理 is a manager.")),
                    lesson("Small Talk", "Chat about the weekend with a colleague.", "How was your weekend?", 300,
                            List.of(w("你好吗？", "nǐ hǎo ma?", "How are you?"), w("我最近很忙。", "wǒ zuìjìn hěn máng.", "I've been busy lately."),
                                    w("周末你做什么？", "zhōumò nǐ zuò shénme?", "What are you doing at the weekend?")),
                            List.of(w("周末", "zhōumò", "weekend"), w("最近", "zuìjìn", "lately"), w("忙", "máng", "busy"),
                                    w("休息", "xiūxi", "to rest")),
                            List.of(w("忙", "máng", "busy"), w("休", "xiū", "to rest")),
                            "Small Talk Phrases", "Match the Small Talk Words",
                            when("It's Friday and you ask a colleague about their plans. What do you say?", 2, "周末 is the weekend.")),
                    lesson("Lunch with Colleagues", "Suggest lunch and offer to pay.", "Lunch break", 280,
                            List.of(w("我们一起吃饭吧。", "wǒmen yìqǐ chīfàn ba.", "Let's eat together."), w("你想吃什么？", "nǐ xiǎng chī shénme?", "What do you want to eat?"),
                                    w("我请你。", "wǒ qǐng nǐ.", "It's my treat.")),
                            List.of(w("一起", "yìqǐ", "together"), w("午饭", "wǔfàn", "lunch"), w("请", "qǐng", "to treat"),
                                    w("食堂", "shítáng", "canteen")),
                            List.of(w("午", "wǔ", "noon"), w("饭", "fàn", "meal")),
                            "Lunch Phrases", "Match the Lunch Words",
                            when("You want to pay for your colleague's lunch. What do you say?", 2, "请 here means to treat someone.")))),
            new UnitSpec("Calls and Messages", List.of(
                    lesson("On the Phone", "Answer the phone and ask who is calling.", "Hello, who's speaking?", 280,
                            List.of(w("喂，你好！", "wéi, nǐ hǎo!", "Hello!"), w("请问你是谁？", "qǐngwèn nǐ shì shéi?", "May I ask who is calling?"),
                                    w("请稍等。", "qǐng shāo děng.", "One moment, please.")),
                            List.of(w("打电话", "dǎ diànhuà", "to make a call"), w("号码", "hàomǎ", "number"), w("喂", "wéi", "hello (on the phone)"),
                                    w("手机", "shǒujī", "mobile phone")),
                            List.of(w("电", "diàn", "electricity"), w("话", "huà", "speech")),
                            "Phone Phrases", "Match the Phone Words",
                            when("You need the caller to wait a little. What do you say?", 2, "稍等 is “wait a moment”.")),
                    lesson("Messages", "Send short messages to your team.", "Short messages", 270,
                            List.of(w("我晚一点到。", "wǒ wǎn yìdiǎn dào.", "I'll be a bit late."), w("明天开会。", "míngtiān kāihuì.", "There's a meeting tomorrow."),
                                    w("收到，谢谢！", "shōudào, xièxie!", "Got it, thanks!")),
                            List.of(w("信息", "xìnxī", "message"), w("开会", "kāihuì", "to have a meeting"), w("收到", "shōudào", "to receive"),
                                    w("发", "fā", "to send")),
                            List.of(w("发", "fā", "to send"), w("信", "xìn", "letter")),
                            "Message Phrases", "Match the Message Words",
                            when("Your bus is delayed. What do you text your manager?", 0, "晚 is late.")))));

    static final List<UnitSpec> SERVING_CUSTOMERS = List.of(
            new UnitSpec("At the Counter", List.of(
                    lesson("Greeting Customers", "Welcome customers and ask what they would like.", "Welcome!", 310,
                            List.of(w("欢迎光临！", "huānyíng guānglín!", "Welcome!"), w("请坐。", "qǐng zuò.", "Please sit down."),
                                    w("您要什么？", "nín yào shénme?", "What would you like?")),
                            List.of(w("欢迎", "huānyíng", "welcome"), w("您", "nín", "you (polite)"), w("坐", "zuò", "to sit"),
                                    w("客人", "kèrén", "customer")),
                            List.of(w("请", "qǐng", "please"), w("坐", "zuò", "to sit")),
                            "Welcome Phrases", "Match the Welcome Words",
                            when("A customer walks into the shop. What do you say first?", 0, "欢迎 is “welcome”.")),
                    lesson("Giving Prices", "Say how much something costs and give change.", "Giving prices", 290,
                            List.of(w("这个二十块。", "zhège èrshí kuài.", "This one is twenty ringgit."), w("一共五十块。", "yígòng wǔshí kuài.", "Fifty ringgit altogether."),
                                    w("这是找您的钱。", "zhè shì zhǎo nín de qián.", "Here is your change.")),
                            List.of(w("价格", "jiàgé", "price"), w("一共", "yígòng", "altogether"), w("找钱", "zhǎo qián", "to give change"),
                                    w("块", "kuài", "ringgit, dollar")),
                            List.of(w("价", "jià", "price"), w("钱", "qián", "money")),
                            "Price Phrases", "Match the Counter Words",
                            when("You add up two items for the customer. What do you say?", 1, "一共 is “altogether”.")))),
            new UnitSpec("Helping Customers", List.of(
                    lesson("Simple Questions", "Ask what a customer wants and how many.", "Can I help you?", 280,
                            List.of(w("您想买什么？", "nín xiǎng mǎi shénme?", "What would you like to buy?"), w("您要几个？", "nín yào jǐ ge?", "How many would you like?"),
                                    w("有，在这儿。", "yǒu, zài zhèr.", "Yes, it's here.")),
                            List.of(w("几", "jǐ", "how many"), w("有", "yǒu", "to have"), w("没有", "méiyǒu", "not to have"),
                                    w("这儿", "zhèr", "here")),
                            List.of(w("几", "jǐ", "how many"), w("有", "yǒu", "to have")),
                            "Question Phrases", "Match the Answer Words",
                            when("A customer asks for apples and you need the number. What do you ask?", 1, "几个 asks how many.")),
                    lesson("Thanking Customers", "Thank customers and say goodbye.", "Goodbye and thank you", 270,
                            List.of(w("谢谢光临！", "xièxie guānglín!", "Thank you for coming!"), w("不客气。", "bú kèqi.", "You're welcome."),
                                    w("请慢走。", "qǐng màn zǒu.", "Take care.")),
                            List.of(w("光临", "guānglín", "to visit"), w("慢", "màn", "slow"), w("客气", "kèqi", "polite"),
                                    w("再来", "zài lái", "to come again")),
                            List.of(w("谢", "xiè", "to thank"), w("慢", "màn", "slow")),
                            "Thank You Phrases", "Match the Thank You Words",
                            when("A customer thanks you. What do you say?", 1, "不客气 answers 谢谢.")))));

    // ----- quizzes -----

    /**
     * The lesson's quiz (2 marks a question): its own when it has one, else built from its words and phrases: two word
     * meanings, the situation, what a sentence means and which phrase you hear. Options rotate so the right one is not
     * always first.
     */
    static List<Question> quiz(LessonSpec spec) {
        if (!spec.quiz().isEmpty()) {
            return spec.quiz();
        }
        List<Word> phrases = spec.phrases();
        Word reply = phrases.get(spec.situation().reply());
        List<Word> sentences = phrases.stream().filter(phrase -> phrase != reply).toList();
        Word sentence = sentences.get(0);
        Word heard = sentences.get(sentences.size() - 1);
        List<Question> questions = new ArrayList<>();
        questions.add(wordMeaning(spec.words(), spec.words().get(0), 1, "Say it aloud: " + spec.words().get(0).pinyin() + "."));
        questions.add(wordMeaning(spec.words(), spec.words().get(1), 2, null));
        questions.add(new Question("MULTIPLE_CHOICE", spec.situation().prompt(), phraseOptions(phrases, reply, 1),
                reply.chinese() + " (" + reply.pinyin() + ") means “" + reply.meaning() + "”.",
                spec.situation().hint(), null));
        questions.add(new Question("MULTIPLE_CHOICE", "What does " + sentence.chinese() + " (" + sentence.pinyin() + ") mean?",
                rotate(phrases.stream().map(phrase -> new Option(phrase.meaning(), phrase == sentence)).toList(), 3),
                sentence.chinese() + " (" + sentence.pinyin() + ") means “" + sentence.meaning() + "”.", null, null));
        questions.add(new Question("LISTEN_CHOOSE", "Listen and choose: which phrase did you hear?",
                phraseOptions(phrases, heard, 2),
                "You heard " + heard.chinese() + " (" + heard.pinyin() + "): “" + heard.meaning() + "”.", null, heard.chinese()));
        return questions;
    }

    private static Question wordMeaning(List<Word> words, Word word, int rotateBy, String hint) {
        return new Question("MULTIPLE_CHOICE", "What does " + word.chinese() + " mean?",
                rotate(words.stream().map(other -> new Option(other.meaning(), other == word)).toList(), rotateBy),
                word.chinese() + " (" + word.pinyin() + ") means “" + word.meaning() + "”.", hint, null);
    }

    private static List<Option> phraseOptions(List<Word> phrases, Word answer, int rotateBy) {
        return rotate(phrases.stream().map(phrase -> zh(phrase.chinese(), phrase.pinyin(), phrase == answer)).toList(), rotateBy);
    }

    /** An answer in Chinese with its pinyin on a second line (Level 1 answers show pinyin). */
    private static Option zh(String chinese, String pinyin, boolean correct) {
        return new Option(chinese + "\n" + pinyin, correct);
    }

    private static List<Option> rotate(List<Option> options, int by) {
        List<Option> rotated = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            rotated.add(options.get((index + by) % options.size()));
        }
        return rotated;
    }
}
