package com.example.aichinese.config.seed;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * The level check question bank that comes with the platform: 48 published questions in the 8 skills, from Beginner 1
 * easy to Beginner 2 hard (the right answer first in each list), and one draft. The check asks one question per skill,
 * so every skill has questions across the difficulty steps; idioms are the stretch question.
 */
final class LevelCheckContent {

    private static final String LISTEN = "Listen and choose";
    private static final String WHICH_NUMBER = "Which number is this?";
    private static final String FILL_GAP = "Fill in the gap";
    private static final String IDIOM = "What does this idiom mean?";

    private LevelCheckContent() {
    }

    record Option(String text, boolean correct, String explanation) {
    }

    record Question(
            String prompt,
            String hanzi,
            String pinyin,
            String audioText,
            LevelCheckSkill skill,
            ActivityDifficulty difficulty,
            LevelCheckLevel level,
            PublishStatus status,
            List<Option> options) {
    }

    static List<Question> questions() {
        return List.of(
                // Beginner 1 · easy
                b1(ActivityDifficulty.EASY, LevelCheckSkill.VOCABULARY, "What does 你好 mean?", "你好", "nǐ hǎo", null,
                        right("Hello", "nǐ hǎo = hello"), wrong("Thank you", "That's 谢谢"), wrong("Goodbye", "That's 再见")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.CHARACTERS, "Which character means “I / me”?", null, null, null,
                        right("我", "我 (wǒ) = I, me"), wrong("你", "你 (nǐ) = you"), wrong("他", "他 (tā) = he")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.VOCABULARY, "What does 谢谢 mean?", "谢谢", "xièxie", null,
                        right("Thank you", "xièxie = thank you"), wrong("Sorry", "That's 对不起"), wrong("Please", "That's 请")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.PINYIN, "Choose the pinyin for 再见", "再见", null, null,
                        right("zàijiàn", "再见 = goodbye"), wrong("zǎoshang", "That's 早上, morning"),
                        wrong("xièxie", "That's 谢谢, thank you")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.NUMBERS, "What does 八 mean?", "八", "bā", null,
                        right("eight", "八 (bā) = eight"), wrong("six", "That's 六"), wrong("four", "That's 四")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.NUMBERS, WHICH_NUMBER, "二十八", "èrshíbā", null,
                        right("28", "二十八 is two tens and eight"), wrong("82", "That's 八十二"), wrong("18", "That's 十八"),
                        wrong("208", "That's 二百零八")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.LISTENING, LISTEN, null, null, "你好",
                        right("你好", "你好 = hello"), wrong("再见", "再见 = goodbye"), wrong("谢谢", "谢谢 = thank you")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.GRAMMAR, FILL_GAP, "我 ＿＿ 学生。",
                        "Wǒ ___ xuésheng. · “I am a student.”", null,
                        right("是", "是 (shì) links two nouns: 我是学生。"), wrong("叫", "叫 gives a name: 我叫 Amira。"),
                        wrong("有", "有 is to have"), wrong("吗", "吗 ends a question")),
                b1(ActivityDifficulty.EASY, LevelCheckSkill.SENTENCES, "What does 我喝水 mean?", "我喝水", "wǒ hē shuǐ", null,
                        right("I drink water.", "喝 (hē) = to drink, 水 (shuǐ) = water"), wrong("I eat rice.", "That's 我吃饭"),
                        wrong("You drink water.", "That's 你喝水")),
                // Beginner 1 · medium
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.LISTENING, LISTEN, null, null, "早上好",
                        right("早上好", "早上好 = good morning"), wrong("晚上好", "晚上好 = good evening"),
                        wrong("你好", "你好 = hello")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.SENTENCES, "What does 我叫 Alex mean?", "我叫 Alex", "wǒ jiào Alex", null,
                        right("My name is Alex.", "叫 (jiào) gives a name"), wrong("I am Alex's friend.", "That would be 我是 Alex 的朋友"),
                        wrong("I like Alex.", "That would be 我喜欢 Alex")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.VOCABULARY, "What does 多少钱？ mean?", "多少钱？", "duōshao qián?", null,
                        right("How much is it?", "多少 = how much, 钱 = money"), wrong("Where is it?", "That's 在哪儿？"),
                        wrong("What time is it?", "That's 几点？")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.PINYIN, "Choose the pinyin for 名字", "名字", null, null,
                        right("míngzi", "名字 = name"), wrong("mǐnzì", "Check the tones: míng, then a light zi"),
                        wrong("mínzú", "That's 民族, a nation")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.LISTENING, LISTEN, null, null, "一杯茶",
                        right("一杯茶", "一杯茶 = a cup of tea"), wrong("一杯水", "一杯水 = a glass of water"),
                        wrong("一碗饭", "一碗饭 = a bowl of rice")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.SENTENCES, "Which sentence means “I am a student”?", null, null, null,
                        right("我是学生。", "学生 (xuésheng) = student"), wrong("我是老师。", "老师 = teacher"),
                        wrong("你是学生。", "你 = you")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.CHARACTERS, "Which character means “person”?", null, null, null,
                        right("人", "人 (rén) = person"), wrong("大", "大 (dà) = big"), wrong("入", "入 (rù) = to enter")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.NUMBERS, WHICH_NUMBER, "九十九", "jiǔshíjiǔ", null,
                        right("99", "九十九 is nine tens and nine"), wrong("19", "That's 十九"), wrong("90", "That's 九十"),
                        wrong("909", "That's 九百零九")),
                b1(ActivityDifficulty.MEDIUM, LevelCheckSkill.GRAMMAR, FILL_GAP, "你是学生 ＿＿？",
                        "Nǐ shì xuésheng ___? · “Are you a student?”", null,
                        right("吗", "吗 (ma) at the end makes a yes or no question"), wrong("呢", "呢 asks “and you?”"),
                        wrong("的", "的 shows belonging"), wrong("很", "很 goes before an adjective")),
                // Beginner 1 · hard
                b1(ActivityDifficulty.HARD, LevelCheckSkill.SENTENCES, "What does 我不吃辣 mean?", "我不吃辣", "wǒ bù chī là", null,
                        right("I don't eat spicy food.", "不 before 吃 says no"), wrong("I like spicy food.", "That's 我喜欢吃辣"),
                        wrong("The food is not spicy.", "That's 菜不辣")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.LISTENING, LISTEN, null, null, "你叫什么名字？",
                        right("你叫什么名字？", "What's your name?"), wrong("你是哪国人？", "Where are you from?"),
                        wrong("你去哪里？", "Where are you going?")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.VOCABULARY, "What does 图书馆 mean?", "图书馆", "túshūguǎn", null,
                        right("library", "图书馆 = library"), wrong("canteen", "That's 食堂"), wrong("classroom", "That's 教室")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.CHARACTERS, "Which character means “to study”?", null, null, null,
                        right("学", "学 (xué) = to study"), wrong("字", "字 (zì) = character, word"),
                        wrong("写", "写 (xiě) = to write")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.NUMBERS, "How do you say 15?", null, null, null,
                        right("十五", "十五 is ten and five"), wrong("五十", "五十 is five tens: 50"),
                        wrong("五", "五 is 5"), wrong("一五", "Fifteen is said 十五, not 一五")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.PINYIN, "Choose the pinyin for 学生", "学生", null, null,
                        right("xuésheng", "学生 = student"), wrong("xuéxiào", "That's 学校, school"),
                        wrong("xiǎoshuō", "That's 小说, a novel")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.GRAMMAR, FILL_GAP, "我 ＿＿ 喝茶。",
                        "Wǒ ___ hē chá. · “I don't drink tea.”", null,
                        right("不", "不 (bù) before the verb says you don't do it"), wrong("很", "很 goes before an adjective"),
                        wrong("也", "也 means also"), wrong("吗", "吗 ends a question")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.IDIOMS, IDIOM, "马马虎虎", "mǎmǎhūhū · word by word: horse horse tiger tiger", null,
                        right("So-so", "马马虎虎 = so-so, not very good"), wrong("Very fast", "That would be 很快"),
                        wrong("Afraid of tigers", "It has nothing to do with real tigers"),
                        wrong("Good at riding", "It has nothing to do with real horses")),
                // Beginner 2 · easy
                b2(ActivityDifficulty.EASY, LevelCheckSkill.VOCABULARY, "What does 便宜 mean?", "便宜", "piányi", null,
                        right("cheap", "便宜 = cheap"), wrong("expensive", "That's 贵"), wrong("new", "That's 新")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.PINYIN, "Choose the pinyin for 星期天", "星期天", null, null,
                        right("xīngqītiān", "星期天 = Sunday"), wrong("xīngqīyī", "That's 星期一, Monday"),
                        wrong("xīngqīliù", "That's 星期六, Saturday")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.LISTENING, LISTEN, null, null, "现在几点？",
                        right("现在几点？", "What time is it now?"), wrong("今天星期几？", "What day is it today?"),
                        wrong("你几岁？", "How old are you?")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.CHARACTERS, "Which character means “to buy”?", null, null, null,
                        right("买", "买 (mǎi) = to buy"), wrong("卖", "卖 (mài) = to sell"), wrong("读", "读 (dú) = to read")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.NUMBERS, WHICH_NUMBER, "一百", "yìbǎi", null,
                        right("100", "百 (bǎi) = hundred"), wrong("10", "That's 十"), wrong("1000", "That's 一千"),
                        wrong("11", "That's 十一")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.GRAMMAR, FILL_GAP, "我昨天去 ＿＿ 学校。",
                        "Wǒ zuótiān qù ___ xuéxiào. · “I went to school yesterday.”", null,
                        right("了", "了 (le) after the verb shows a completed action"), wrong("吗", "吗 ends a question"),
                        wrong("呢", "呢 asks “and you?”"), wrong("很", "很 goes before an adjective")),
                b2(ActivityDifficulty.EASY, LevelCheckSkill.IDIOMS, IDIOM, "一路平安", "yílù píng'ān · word by word: one road, safe", null,
                        right("Have a safe journey", "You say 一路平安 to someone who is leaving"),
                        wrong("Take the first road", "It is a wish, not a direction"),
                        wrong("The road is closed", "It is a wish, not news about a road"),
                        wrong("Walk more slowly", "That would be 慢慢走")),
                // Beginner 2 · medium
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.SENTENCES, "What does 我可以试一下吗？ mean?", "我可以试一下吗？",
                        "wǒ kěyǐ shì yíxià ma?", null,
                        right("Can I try it on?", "试 (shì) = to try"), wrong("Can I pay by card?", "That's 可以刷卡吗？"),
                        wrong("Can you help me?", "That's 你可以帮我吗？")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.VOCABULARY, "What does 有空 mean?", "有空", "yǒu kòng", null,
                        right("to be free", "有空 = to have free time"), wrong("to be busy", "That's 忙"),
                        wrong("to be late", "That's 迟到")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.LISTENING, LISTEN, null, null, "我希望去中国。",
                        right("我希望去中国。", "I hope to go to China."), wrong("我已经去过中国。", "I have been to China."),
                        wrong("我不想去中国。", "I don't want to go to China.")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.CHARACTERS, "Which character means “to sell”?", null, null, null,
                        right("卖", "卖 (mài) = to sell"), wrong("买", "买 (mǎi) = to buy"), wrong("读", "读 (dú) = to read")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.PINYIN, "Choose the pinyin for 便宜", "便宜", null, null,
                        right("piányi", "便宜 = cheap"), wrong("piàoliang", "That's 漂亮, beautiful"),
                        wrong("pángbiān", "That's 旁边, beside")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.GRAMMAR, FILL_GAP, "我有三 ＿＿ 书。",
                        "Wǒ yǒu sān ___ shū. · “I have three books.”", null,
                        right("本", "本 (běn) is the measure word for books"), wrong("个", "个 is the general measure word, not for books"),
                        wrong("只", "只 counts small animals"), wrong("杯", "杯 counts cups")),
                b2(ActivityDifficulty.MEDIUM, LevelCheckSkill.IDIOMS, IDIOM, "一心一意", "yìxīn yíyì · word by word: one heart, one mind", null,
                        right("With all your heart", "一心一意 = wholeheartedly, with full attention"),
                        wrong("One after another", "That would be 一个一个"),
                        wrong("Changing your mind", "It means the opposite"),
                        wrong("Thinking of nothing", "It means thinking of one thing only")),
                // Beginner 2 · hard
                b2(ActivityDifficulty.HARD, LevelCheckSkill.SENTENCES, "What does 这次考试不难 mean?", "这次考试不难",
                        "zhè cì kǎoshì bù nán", null,
                        right("This exam isn't hard.", "不难 = not difficult"), wrong("This exam was very hard.", "That's 很难"),
                        wrong("There is no exam this time.", "That's 这次没有考试")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.VOCABULARY, "Which word means “colleague”?", null, null, null,
                        right("同事", "同事 (tóngshì) = colleague"), wrong("同学", "同学 = classmate"),
                        wrong("朋友", "朋友 = friend")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.PINYIN, "Choose the pinyin for 漂亮", "漂亮", null, null,
                        right("piàoliang", "漂亮 = beautiful"), wrong("piányi", "That's 便宜, cheap"),
                        wrong("pǎobù", "That's 跑步, running")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.CHARACTERS, "Which character means “expensive”?", null, null, null,
                        right("贵", "贵 (guì) = expensive"), wrong("钱", "钱 (qián) = money"), wrong("买", "买 (mǎi) = to buy")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.NUMBERS, WHICH_NUMBER, "三百五十", "sānbǎi wǔshí", null,
                        right("350", "三百 is 300 and 五十 is 50"), wrong("305", "That's 三百零五"), wrong("3050", "That's 三千零五十"),
                        wrong("35", "That's 三十五")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.GRAMMAR, FILL_GAP, "今天 ＿＿ 昨天忙。",
                        "Jīntiān ___ zuótiān máng. · “Today is busier than yesterday.”", null,
                        right("比", "A 比 B + adjective compares A with B"), wrong("更", "更 means even more, but it needs 比 here"),
                        wrong("最", "最 means the most"), wrong("很", "很 does not compare")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.IDIOMS, IDIOM, "画蛇添足", "huà shé tiān zú · word by word: draw a snake, add feet", null,
                        right("To spoil something by overdoing it", "Adding feet to a finished snake ruins the drawing"),
                        wrong("To be very good at drawing", "The drawing is ruined by the extra feet"),
                        wrong("To finish a job quickly", "It is about doing too much, not about speed"),
                        wrong("To have good luck twice", "It has nothing to do with luck")),
                b2(ActivityDifficulty.HARD, LevelCheckSkill.IDIOMS, IDIOM, "入乡随俗", "rù xiāng suí sú · word by word: enter a place, follow its customs", null,
                        right("When in a new place, follow its customs", "入乡 is to enter a place and 随俗 to follow its customs"),
                        wrong("Go back to your hometown", "It is about the place you are visiting"),
                        wrong("Teach your customs to others", "It means the opposite"),
                        wrong("Move to the countryside", "乡 here means any place you go to")),
                new Question("Choose the pinyin for 谢谢", "谢谢", null, null, LevelCheckSkill.PINYIN, ActivityDifficulty.HARD,
                        LevelCheckLevel.BEGINNER_2, PublishStatus.DRAFT, List.of(
                                right("xièxie", "谢谢 = thank you"), wrong("xiéxié", "The second 谢 is light"),
                                wrong("xièxiè", "The second 谢 is light"))));
    }

    private static Question b1(ActivityDifficulty difficulty, LevelCheckSkill skill, String prompt, String hanzi,
            String pinyin, String audioText, Option... options) {
        return new Question(prompt, hanzi, pinyin, audioText, skill, difficulty, LevelCheckLevel.BEGINNER_1,
                PublishStatus.PUBLISHED, List.of(options));
    }

    private static Question b2(ActivityDifficulty difficulty, LevelCheckSkill skill, String prompt, String hanzi,
            String pinyin, String audioText, Option... options) {
        return new Question(prompt, hanzi, pinyin, audioText, skill, difficulty, LevelCheckLevel.BEGINNER_2,
                PublishStatus.PUBLISHED, List.of(options));
    }

    private static Option right(String text, String explanation) {
        return new Option(text, true, explanation);
    }

    private static Option wrong(String text, String explanation) {
        return new Option(text, false, explanation);
    }
}
