package com.example.aichinese.config.seed;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * The level check question bank that comes with the platform: 24 published questions from Beginner 1 easy to
 * Beginner 2 hard (the right answer first in each list), and one draft.
 */
final class LevelCheckContent {

    private static final String LISTEN = "Listen and choose";

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
                b1(ActivityDifficulty.EASY, LevelCheckSkill.VOCABULARY, "What does 八 mean?", "八", "bā", null,
                        right("eight", "八 (bā) = eight"), wrong("six", "That's 六"), wrong("four", "That's 四")),
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
                // Beginner 1 · hard
                b1(ActivityDifficulty.HARD, LevelCheckSkill.SENTENCES, "What does 我不吃辣 mean?", "我不吃辣", "wǒ bù chī là", null,
                        right("I don't eat spicy food.", "不 before 吃 says no"), wrong("I like spicy food.", "That's 我喜欢吃辣"),
                        wrong("The food is not spicy.", "That's 菜不辣")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.LISTENING, LISTEN, null, null, "你叫什么名字？",
                        right("你叫什么名字？", "What's your name?"), wrong("你是哪国人？", "Where are you from?"),
                        wrong("你去哪里？", "Where are you going?")),
                b1(ActivityDifficulty.HARD, LevelCheckSkill.VOCABULARY, "What does 图书馆 mean?", "图书馆", "túshūguǎn", null,
                        right("library", "图书馆 = library"), wrong("canteen", "That's 食堂"), wrong("classroom", "That's 教室")),
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
