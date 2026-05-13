# Proposal Revision Guide

## 这份 proposal 现在最容易被扣分的地方

1. **像“做完项目后的总结”，而不是“做项目之前的提案”**  
   你的内容整体已经比很多学生好，但有些地方还是太像 final report 的倒影。高分 proposal 必须持续使用 *proposed system / this study proposes / the prototype will / the study is expected to* 这一类前瞻性表达，而不是让老师感觉你只是把已完成项目倒推成 proposal。

2. **马来西亚语境还不够“落地”**  
   你已经提到 Malaysia，但还可以更像 UPM 的 FYP：强调马来西亚的多语环境、Bahasa Melayu 与 English 作为桥接语言、华小/补习中心/课后 enrichment 场景、教师与家长监督的重要性。

3. **Problem Statement 还可以更锋利**  
   现在的问题写得不少，但有些是“现象描述”，还不够像“学术问题 + 系统问题 + 为什么值得做”。高分写法要更清楚地形成链条：  
   `beginner difficulty -> fragmented tools -> weak formative feedback -> teacher workload -> child-safe AI gap -> need for integrated platform`

4. **4.3 Dataset Scope 缺失**  
   这是一个非常重要的结构性失分点。模板明确有 `4.3 Dataset Scope`，你目前没有写。老师一看就会觉得“模板没有完全遵循”。这一项必须补上。

5. **Methodology 还不够“可执行、可评估、可答辩”**  
   你现在的方法论方向是对的，但如果想冲顶分，需要更明确：
   - 计划找什么人做访谈或评估
   - 采用什么抽样方式
   - 问卷测什么维度
   - 数据怎样分析
   - 如果无法直接测试小学生，proxy users 怎么说明限制

6. **ROI/ROV 太泛，缺少马来西亚式量化**  
   现在写 Free / Low 不够强。高分 proposal 更好写法是直接用 MYR 粗估，例如 AI API 测试预算、可选 demo hosting、contingency，总成本区间 RM100-RM250 之类，会显得更成熟。

7. **Research Gap 还可以更“研究型”，不能只像产品功能清单**  
   评审要看的不是“你功能很多”，而是“为什么现有研究和现有系统之间仍有空白，而你的项目正好填补这个空白”。

8. **References 有潜在风险**  
   模板写了 “References must be cited based on current research article (5 years back).”  
   从 2026 往前推，最安全的是 **2021-2026**。你现在有不少经典文献或较旧文献：
   - Liu (2018)
   - Roorda et al. (2017)
   - Sweller et al. (2019)
   - van de Pol et al. (2010)
   - Vygotsky (1978)
   - Wallace and Leong (2020)
   - Yang (2020)
   - Zhonggen (2019)
   - Checa and Bustillo (2020)

   这些不是不能用，但如果老师严格按“5 years back”看，你就不能让旧文献占太多。最稳妥做法是：
   - 保留少量经典理论文献，只放在 `5.3 Theoretical Framework / Models`
   - 其余论证尽量由 2021-2026 的研究文章支撑
   - 竞品官网可以保留用于系统比较，但不要把它们当作“研究证据”

## 你应该怎么改

## 一、必须整段替换的部分

这些部分建议直接用新版，不要只做零碎修补：

- `Title`
- `1.1 Background of the Study`
- `1.2 Motivation of the Study`
- `1.3 Project Overview`
- `2.1 Problem Identification`
- `2.2 Problem Justification`
- `2.3 Impact of the Problem`
- `3.1 General Objective`
- `3.2 Specific Objectives`
- `4.1 System Scope`
- `4.2 User Scope`
- `4.3 Dataset Scope`  **必须新增**
- `5.3 Theoretical Framework / Models`
- `5.4 Existing Systems / Competitive Landscape`
- `5.5 Research Gap Analysis`
- `6.2 Research Design`
- `6.3 Development Methodology`
- `6.4 Data Collection Methods`
- `6.6 Software Verification and Validation`
- `6.7 Evaluation Methods`
- `7.1 Cost-Benefit Analysis`
- `7.2 Expected Benefits`
- `7.3 Value Contribution to Stakeholders`
- `8.1 Work Breakdown Structure`
- `8.2 Gantt Chart`
- `9.1 Summary of Proposal`
- `9.2 Expected Contributions`

## 二、只需小修的部分

- `Front page`
  - 把标题改成 proposal 风格，不要像最终产品宣传标题。
  - 建议标题：  
    **Proposed Design and Development of an AI-Powered Interactive Chinese Learning Platform for Young Learners in Malaysia**

- `Table of Contents`
  - 用 Word 自动生成，确保编号、页码、缩进全部统一。

- `List of Tables / Figures / Abbreviations`
  - 这些整体没问题，但要统一标点、大小写、页码间距。

- `Appendix B`
  - 现有题目可继续用，但建议把问卷维度写得更清楚： usability, clarity, motivation, perceived learning support, child-appropriateness。

## 三、最关键的写作原则

### 1. 全文必须保持“未开发视角”

优先使用这些表达：

- `This study proposes...`
- `The proposed prototype will...`
- `The system is intended to...`
- `The study is expected to...`
- `The evaluation will examine...`

避免这些感觉像“已完成”的表达：

- `The system provides...`
- `The platform has...`
- `The project completed...`
- `The dashboard shows...`

### 2. 写成“研究驱动的系统 proposal”，不是“功能推销文案”

高分 proposal 的逻辑是：

- 先说明教育问题
- 再说明现有方法不足
- 再说明理论依据
- 再说明 proposed system 如何回应这些问题
- 最后说明如何验证它值不值得做

### 3. AI 要写成“bounded support”

这是你 proposal 里很加分的一点，但还可以更成熟。你要反复强调：

- AI is a support tool, not a replacement for teachers
- AI output is bounded, reviewable, and age-appropriate
- teacher oversight remains necessary
- the study does not claim long-term learning effectiveness

### 4. 马来西亚视角要更自然地贯穿

你不需要硬塞国家政策，但要自然体现：

- multilingual learning environment
- English and Bahasa Melayu as bridging languages
- private tuition centres / enrichment settings
- child supervision and consent
- realistic cost sensitivity

## 四、References 应该怎么处理最稳

如果你现在时间有限，最稳的策略不是“全部重做”，而是：

1. **保留你当前 2021-2026 的核心实证文献**
   - Rahmat et al. (2021)
   - Chua et al. (2024)
   - Paee et al. (2024)
   - Fang et al. (2024)
   - Du and Daniel (2024)
   - Chen (2024)
   - Wang, S. et al. (2024)
   - Wang, T. (2024)
   - Hongsamsibkao (2024)
   - Garzon et al. (2025)

2. **旧文献只保留少量理论锚点**
   - Vygotsky
   - Sweller
   - van de Pol

3. **竞品官网只用于 5.4 competitor analysis**
   - Duolingo
   - HelloChinese
   - ChineseSkill
   - LingoDeer
   - Skritter

4. **在答辩或导师追问时的说法**
   - `The proposal prioritises recent empirical studies from 2021 to 2026, while a small number of foundational sources are retained only to support the theoretical framework.`

## 五、我已经帮你整理好的可直接用内容

我已经另外写了一份英文版可直接粘贴稿，放在：

- `docs/proposal_top_score_version_en.md`

这份内容已经按以下标准重写：

- 站在“proposal before development”的视角
- 与你现有项目真实功能边界一致
- 强化马来西亚语境
- 补上 `4.3 Dataset Scope`
- 强化 methodology, evaluation, ROV
- 保持软件工程 FYP 的技术可信度

你可以直接把它复制进 Word，再结合你现有的目录、表格、图、甘特图使用。
