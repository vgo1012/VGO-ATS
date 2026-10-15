---

2026-09-22 入职流程交互见[入职流程与共享库退回](../docs/入职流程与共享库退回_2026-09-22.md)：主导航入口现称“录用交付”，页面标题沿用“入职流程”。招聘流程只保留一次“确认录用”弹窗，原因评论下方紧接必选 Offer 进展；确认后先显示金色“入职中”及动态历时，确认实际入职后显示绿色“已入职”、固定历时和实际时间。“确认实际入职／更正入职时间”与“退回共享简历库”在独立“入职操作”列直接使用按钮，“操作”列只保留查看简历。退回弹窗允许不填原因直接提交，也可选受控原因和补充说明。该规则优先于下文旧“已入职”入口、统一列表列定义及三段弹窗描述。

2026-09-18 JD 表单移除区分说明输入，工作地区保留真实地点语义；同名同地区提示用于核对，不阻止独立创建。列表与详情保留完整编号，以区分不同 HR 的 JD；HC 占用与实际入职分别表达，Offer 满额不隐藏未完成入职的超期提醒。业务口径见[JD 独立分配与入职交付](../docs/JD独立分配与入职交付_2026-09-18.md)，优先于下文冲突历史规则。

2026-09-19 招聘流程现行规则见[标签与面试记录](../docs/招聘流程标签与面试记录_2026-09-15.md)：面试类型每轮独立选择、首轮安排后可跳轮、面评文字可空；有效黑名单标签会排除个人待筛选与共享简历库，停用后实时重算。本条优先于下文冲突的历史展示规则。
name: "VGO ATS"
description: "蓝色高密度 AI 招聘工作台"
colors:
  brand-forest: "#001b18"
  brand-paper: "#f4fbf7"
  brand-lime: "#c6ef67"
  brand-orange: "#eba55d"
  system-blue: "#2563eb"
  operator-blue: "#2563eb"
  operator-blue-deep: "#1d4ed8"
  evidence-indigo: "#4f46e5"
  selection-blue: "#eff6ff"
  ink: "#17233f"
  ink-strong: "#0f172a"
  muted: "#596a83"
  border: "#dbe5f4"
  divider: "#eef2f6"
  surface: "#ffffff"
  canvas: "#f5f8ff"
  success: "#16856b"
  warning: "#b66a12"
  danger: "#c33c54"
  info: "#168bd2"
  recommendation-orange: "#c2410c"
  consideration-blue: "#2563eb"
typography:
  brand-display:
    fontFamily: '"Archivo Black", sans-serif'
    fontSize: "clamp(88px, 14.2vw, 224px)"
    fontWeight: 400
    lineHeight: 0.82
    letterSpacing: "-0.048em"
  brand-heading:
    fontFamily: '"Archivo Black", sans-serif'
    fontSize: "clamp(50px, 4.1vw, 72px)"
    fontWeight: 400
    lineHeight: 0.92
    letterSpacing: "-0.034em"
  access-display:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "clamp(42px, 4.3vw, 66px)"
    fontWeight: 600
    lineHeight: 1.28
    letterSpacing: "-0.045em"
  headline:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "30px"
    fontWeight: 600
    lineHeight: 1.3
  title:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "16px"
    fontWeight: 600
  body:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.75
  data:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "12px"
    fontWeight: 600
  action:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "14px"
    fontWeight: 600
  metric:
    fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif'
    fontSize: "25px"
    fontWeight: 700
    lineHeight: 1
rounded:
  score-block: "2px"
  tag: "6px"
  control: "8px"
  profile: "9px"
  alert: "10px"
  panel: "12px"
  public-nav: "14px"
  public-card: "19px"
  circular: "50%"
spacing:
  micro: "4px"
  compact: "8px"
  control: "12px"
  content: "16px"
  panel: "18px"
  page: "24px"
  wide-page: "44px"
components:
  button-primary:
    backgroundColor: "{colors.operator-blue}"
    textColor: "{colors.surface}"
    typography: "{typography.action}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "38px"
  button-primary-hover:
    backgroundColor: "{colors.operator-blue-deep}"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "38px"
  field-workspace:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "0 12px"
    height: "38px"
  field-login:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "0 14px"
    height: "52px"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
  nav-active:
    backgroundColor: "transparent"
    textColor: "{colors.operator-blue}"
    height: "76px"
---

# Design System: VGO ATS

## 品牌

VGO 团队原创标志以向前的 V 形折线和右上角小箭头表达协作与行动。主色采用工作台蓝 `#2563eb`，辅色为浅蓝 `#bfdbfe`；标志的几何路径不依赖外部字体。

- `public/assets/vgo-mark.svg`：方形标志，用于首页与登录页。
- `public/favicon.svg`：浏览器标签页使用透明背景的蓝色 V 形标志，不带方形底框。
- `public/assets/vgo-logo.svg`：完整 VGO 字标，用于导航。
- `public/assets/vgo-talent-connection.png`：首页、登录与首次改密页共用的人才连接插画，使用 ImageGen 根据已选设计方案生成；蓝色 VGO 几何标志连接半透明简历与人物。插画仅作装饰，标题、能力说明和登录入口由 HTML 输出，图片失败不影响登录。

不再使用公司 Logo、吉祥物、视频或内部视觉参考。公开首页提供单一登录入口，登录与改密流程共用原布局，工作台保持原有信息密度，采用蓝色主题。

登录与首次改密页统一使用浅蓝品牌区、白色表单区及蓝色操作控件，复用首页标志与人才连接插画。登录页左侧展示“让人才与机会／在这里相遇”，右侧显示“欢迎回来”和中文说明；窄屏缩为紧凑品牌区，优先显示表单。装饰插画不接收鼠标事件，返回首页、密码显示、账号记忆、帮助和登录动作保留原行为。

公开首页采用浅蓝画布、左文右图的首屏：品牌标志位于左上，蓝色“登录工作台”入口位于右上；主标题为“让人才与机会／在这里相遇”，第二行使用品牌蓝。底部以细分隔线及图标呈现简历解析、智能匹配、招聘协同，不呈现虚构业务数据。窄屏按文案、插画、能力说明纵向排列，允许自然滚动，不再展示日历与每日语录。

## 工作台规则

复用 Ant Design、现有主题与业务组件，局部控件不另建主题。正文默认 14px，辅助说明不低于 12px；列表和图表各自处理滚动，窄屏保留操作和风险信息。导航、表单、弹层保留键盘焦点与可读名称。状态不能仅靠颜色，所有动效服从 `prefers-reduced-motion`。

人工操作、主按钮、导航与选中筛选项统一使用蓝色 `#2563eb`，悬停为 `#1d4ed8`；AI 分析证据用靛蓝 `#4f46e5` 区分，成功、警告、危险状态保留既有语义色。浅蓝画布上仅放置低透明度、静态虚化色块，白色数据面、文字和控件保持清晰。弹窗遮罩轻度虚化后方工作区，不虚化弹窗内容。

日程“今天”和指标筛选选中背景沿用蓝色操作反馈；已完成、周末、超时等业务标记保留各自语义。CSS 成功色 `#16856b`、危险色 `#c33c54` 与 Ant Design 主题一致。收件关联、评分、共享简历转入和趋势明细弹窗复用工作台遮罩、圆角、间距及动效，保留各自的提交与关闭行为。

主导航采用浅蓝色圆角分段条，当前栏目为蓝色胶囊和白色图文；取消底部下划线。桌面保持单行，窄屏导航横向滚动，当前栏目自动进入可见区域；键盘焦点与减少动态效果设置继续生效。

右上角账号入口只显示一行姓名，配浅蓝用户图标。账号菜单将姓名、角色和邮箱放在信息区，角色与姓名相同时不重复显示；下方保留工作区设置与退出登录，支持键盘打开和 Escape 返回入口。

招聘分析采用浅蓝画布、白色分析面板和蓝色数据重点。顶部依次呈现统计筛选与四项真实指标；流程转化和招聘趋势并列，下方展示地区分布、招聘需求与风险，以及年龄、学历和渠道。窄屏按阅读顺序纵向排列，流程及员工表仅在各自区域滚动。

地区分布使用带数量的排名条，保留接口原始地点标签、未知地区和多地点记录，不推测或合并城市。不再使用地图格网、发光轨道和循环装饰动效；保留数值、图表的短过渡并服从 `prefers-reduced-motion`。流程百分比以进入流程为基数，入库人数独立统计，不通过结果不连接到录用路径。地区目录的来源与限制见 `src/demand/cities-2023.README.md`。

Archivo Black 与 Noto Sans SC 字体保留同目录许可，不从公司素材中提取字体。
