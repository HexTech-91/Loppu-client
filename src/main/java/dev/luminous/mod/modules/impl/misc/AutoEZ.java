package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class AutoEZ extends Module {
    public enum Type {
        Bot,
        Custom,
        AutoSex,
        Lolita
    }

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final EnumSetting<Type> type = this.add(new EnumSetting<>("Type", Type.Bot));
    private final SliderSetting range = this.add(new SliderSetting("Range", 10.0, 0.0, 20.0, 0.1));

    // 10条自定义消息槽
    private final StringSetting message = this.add(new StringSetting("Message", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message2 = this.add(new StringSetting("Message2", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message3 = this.add(new StringSetting("Message3", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message4 = this.add(new StringSetting("Message4", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message5 = this.add(new StringSetting("Message5", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message6 = this.add(new StringSetting("Message6", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message7 = this.add(new StringSetting("Message7", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message8 = this.add(new StringSetting("Message8", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message9 = this.add(new StringSetting("Message9", "", () -> type.getValue() == Type.Custom));
    private final StringSetting message10 = this.add(new StringSetting("Message10", "", () -> type.getValue() == Type.Custom));

    private final SliderSetting randoms = this.add(new SliderSetting("Random", 3.0, 0.0, 20.0, 1.0));

    // 本地图腾计数映射（使用玩家名作为键）
    private final Map<String, Integer> localPopCount = new HashMap<>();

    // 定期清理计时器（每30秒清理一次）
    private final Timer cleanupTimer = new Timer();
    private static final long CLEANUP_INTERVAL_MS = 30_000; // 30秒

    public AutoEZ() {
        super("AutoEZ", Module.Category.Misc);
        this.setChinese("自动嘲讽");
    }

    @Override
    public void onDisable() {
        localPopCount.clear();
    }

    // AutoSex 预设
    public final List<String> sex = List.of(
            "呐呐~杂鱼哥哥不会这样就被捉弄的不会说话了吧♡",
            "嘻嘻~杂鱼哥哥不会以为竖个大拇哥就能欺负我了吧~不会吧♡不会吧♡",
            "杂鱼哥哥怎么可能欺负得了别人呢~只能欺负自己哦♡~",
            "哥哥真是好欺负啊♡嘻嘻~",
            "哎♡~杂鱼说话就是无趣唉~",
            "呐呐~杂鱼哥哥发这个是想教育我吗~嘻嘻~怎么可能啊♡",
            "什么嘛~废柴哥哥会想这种事情啊~唔呃",
            "把你肮脏的目光拿开啦~很恶心哦♡",
            "咱的期待就是被你这样的笨蛋破坏了~♡",
            "杂鱼哥哥今天也努力地送人头了呢♡",
            "诶嘿~杂鱼哥哥的挣扎像小丑一样可爱哦♡",
            "这样的操作也敢来挑战我？杂鱼哥哥真勇敢呢♡",
            "呐呐，杂鱼哥哥是不是又在偷偷哭鼻子了♡",
            "杂鱼哥哥的败北，就是我的快乐源泉呢♡",
            "什么嘛，杂鱼哥哥原来就这点水平呀♡",
            "别灰心哦杂鱼哥哥，下次一定会输得更惨的♡",
            "杂鱼哥哥的自信是从哪里来的呀？教教我嘛♡",
            "嘻嘻，杂鱼哥哥连逃跑的样子都这么滑稽♡",
            "呐，杂鱼哥哥要不要考虑换个游戏玩？这个不适合你♡",
            "杂鱼哥哥今天又带来了欢乐，谢谢你啦♡",
            "哎呀，杂鱼哥哥被欺负得说不出话了呢~真可爱♡",
            "杂鱼哥哥的每个失误都让我好开心呢，继续继续♡",
            "嘛，杂鱼哥哥也就只能在这种时候耍帅了♡",
            "杂鱼哥哥的尊严就像纸糊的一样，一戳就破呢♡",
            "(｡•̀ᴗ-)✧ 杂鱼哥哥加油哦，反正也赢不了♡"
    );

    // Bot 预设
    public List<String> bot = List.of(
            "没有谁天生倔强，只是为了梦想，寸步不让。",
            "哪有什么开挂的人生，不过是厚积薄发。",
            "不能在头脑中激情澎湃了一会儿就全当努力过了。",
            "少壮不努力，老大怪水逆。",
            "某一天，你所坚持的和你所承担过的一切，会有回应，它们并非失去踪迹，只是需要时间抵达。",
            "生活每天带给你的惊喜，就像一个无限集合里的每个元素，虽然取之不尽，却又各不一样。",
            "成功就是你被击落到失落的深渊之后反弹的高度。",
            "一切都是最好的安排，一切都是最美的遇见。",
            "成功苦中求，梅花寒里开。",
            "我愿做一条RNA，即使是单链，也能拥有U ------唯美生物",
            "你的微笑或许是一场蝴蝶效应的开始",
            "给时光以生命，而不是给生命以时光",
            "当没有人注视你时，你才是你自己",
            "在什么也不想要的时候，一切如期而来",
            "去过自己另外的生活，你要听话，不是所有的鱼都会生活在同一片海里。",
            "在我不会说话的时候最想说话，在我真正能说，知道的东西越来越多的时候反倒沉默了。",
            "很多事情别想那么糟糕，毕竟，还有阳光来温暖我们的骨头",
            "在跳一个深坑之前，知道它有多深才行",
            "当我们清醒时，黎明才会破晓",
            "知世故而不世故，才是善良的成熟",
            "美好的东西，都是一秒计算的",
            "少年的悲哀，毕竟是易消的残雪",
            "夜坐听风，昼眠听雨",
            "悟得月如何缺，天如何老",
            "一花一世界，一叶一如来",
            "春来花自青，秋至叶飘零",
            "生活总会给你另一个机会，叫做明天",
            "你知道么,这世上有很多不被接受的种子,都在不为人知的角落里开出了花。",
            "请晚霞陪我长大,夜空绽放烟花,我们都在路上,顺着明日的光出发。",
            "这个世界偷偷爱着你,只是你不知道而已。",
            "愿你努力工作,也愿你享受生活;愿你脚踏实地,也愿你仰望星空。",
            "要多多努力,好好生活,做一个可爱的人,做一个闪闪发光的人。",
            "选择其实很简单,静下心来听自己的心声,往自己心里感到踏实的地方走。",
            "望眉目有山河,清澈明朗。愿心中有丘壑,一往无前。",
            "虽然未来藏在迷雾中,叫人看来胆怯,但当你踏足其中,就回云开雾散。",
            "疯狂收集每一个快乐的瞬间,用它们回击每一个糟糕的日子。",
            "即便是最渺小的存在,也胜过那最伟大的虚空。",
            "命,是弱者的借口;运,是强者的谦辞。"
    );

    // Lolita 预设
    public final List<String> lolita = List.of(
            "!误~连这种程度都受不了吗？%player% 果然是低等杂鱼呢~",
            "!杂鱼先生 %player% 又在偷看呢~真是恶心死了~",
            "!嗯~难道杂鱼酱 %player% 脑子里只知道被小女孩辱骂吗？真是又弱又笨诶♡~嘻嘻~",
            "!怎么还有喜欢被骂的杂鱼呀，啧♡~%player% 真是变态！色鬼！臭杂鱼！",
            "!杂鱼哥哥 %player%,看你那副可怜的样子真是太好笑了呢~♡嘻嘻~",
            "!%player% 这样飞舞又讨嫌的杂鱼，很恶心哦♡~嘻嘻嘻~",
            "!怎么就软下去了, %player% 真是杂鱼♡~",
            "!这种程度就投降了？%player% 真是没用的杂鱼呢♡~",
            "!%player% 就是杂鱼，永远都改变不了呢♡~嘻~",
            "!呜哇～%player% 好逊哦♡，这种程度就满足了嘛？",
            "!呐呐，%player% 桑，刚才是在努力挣扎吗？拼命的样子真的好滑稽哦~♡",
            "!呜哇~%player% 这种程度也能叫反抗吗？简直像在给我抓痒一样呢，逊毙了！",
            "!诶？%player% 这就结束了吗？杂鱼的持久力果然和技术一样差劲呢♡~嘻嘻~",
            "!就算给 %player% 一百次机会，结果也不会改变哦，毕竟杂鱼就是用来被踩在脚下的呀！",
            "!这种单方面的虐待，%player% 难道已经开始产生快感了吗？真是无可救药的变态杂鱼呢~",
            "!别过来！%player% 这种浑身散发着失败气息的臭杂鱼，光是名字出现在屏幕上都让人恶心！",
            "!啧♡~%player% 这副窝囊的样子，真是看一眼都会折寿呢，快滚回泉水反省吧！",
            "!居然被我这种小女孩打败了，%player% 该不会正躲在屏幕后面偷偷抹眼泪吧？好逊哦~♡",
            "!呐，%player% 哥哥，以后出门记得戴个头盔，不然像你这种笨蛋杂鱼很容易被路人打死的哦~",
            "!这种垃圾操作...%player% 的大脑里塞的难道都是废料吗？真是可怜的小杂鱼♡~",
            "!杂鱼酱 %player%，要乖乖听话哦♡~",
            "!逊！毙！了！%player%就是个大笨蛋！",
            "!%player% 的骨头软绵绵的，真是好欺负呢~♡",
            "!嘻嘻，%player% 又碎掉了，好可怜哦~"
    );

    private final Random random = new Random();

    // 监听图腾事件，更新本地计数
    @EventListener
    public void onTotem(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == null || player == mc.player || Alien.FRIEND.isFriend(player)) return;

        String name = player.getName().getString();
        localPopCount.put(name, localPopCount.getOrDefault(name, 0) + 1);
    }

    // 定期清理无效的计数（玩家已离线或不存在于世界中）
    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (cleanupTimer.passedMs(CLEANUP_INTERVAL_MS)) {
            cleanupTimer.reset();
            cleanupPopCount();
        }
    }

    private void cleanupPopCount() {
        if (mc.world == null) return;
        Set<String> currentPlayers = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            currentPlayers.add(player.getName().getString());
        }
        localPopCount.keySet().removeIf(name -> !currentPlayers.contains(name));
    }

    @EventListener
    public void onDeath(DeathEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == mc.player || Alien.FRIEND.isFriend(player)) return;

        if (range.getValue() > 0.0 && mc.player.distanceTo(player) > range.getValue()) return;

        String randomString = generateRandomString(randoms.getValueInt());
        if (!randomString.isEmpty()) {
            randomString = " " + randomString;
        }

        String playerName = player.getName().getString();
        int popCount = localPopCount.getOrDefault(playerName, 0);

        switch (type.getValue()) {
            case Bot -> {
                String botMessage = bot.get(random.nextInt(bot.size())) + " " + playerName + randomString;
                mc.getNetworkHandler().sendChatMessage(botMessage);
                localPopCount.remove(playerName);
            }
            case Custom -> {
                List<String> availableMessages = getAvailableCustomMessages();
                if (!availableMessages.isEmpty()) {
                    String selected = availableMessages.get(random.nextInt(availableMessages.size()));
                    String finalMessage = selected
                            .replace("%player%", playerName)
                            .replace("%pop%", String.valueOf(popCount))
                            + randomString;
                    mc.getNetworkHandler().sendChatMessage(finalMessage);
                    localPopCount.remove(playerName);
                }
            }
            case AutoSex -> {
                String sexMessage = sex.get(random.nextInt(sex.size())) + " " + playerName + randomString;
                mc.getNetworkHandler().sendChatMessage(sexMessage);
                localPopCount.remove(playerName);
            }
            case Lolita -> {
                // 修正：正确替换 %player% 占位符，而不是简单拼接
                String selected = lolita.get(random.nextInt(lolita.size()));
                String finalMessage = selected.replace("%player%", playerName) + randomString;
                mc.getNetworkHandler().sendChatMessage(finalMessage);
                localPopCount.remove(playerName);
            }
        }
    }

    // 获取所有非空的自定义消息（10条）
    private List<String> getAvailableCustomMessages() {
        List<String> messages = new ArrayList<>();
        if (isNotEmpty(message)) messages.add(message.getValue());
        if (isNotEmpty(message2)) messages.add(message2.getValue());
        if (isNotEmpty(message3)) messages.add(message3.getValue());
        if (isNotEmpty(message4)) messages.add(message4.getValue());
        if (isNotEmpty(message5)) messages.add(message5.getValue());
        if (isNotEmpty(message6)) messages.add(message6.getValue());
        if (isNotEmpty(message7)) messages.add(message7.getValue());
        if (isNotEmpty(message8)) messages.add(message8.getValue());
        if (isNotEmpty(message9)) messages.add(message9.getValue());
        if (isNotEmpty(message10)) messages.add(message10.getValue());
        return messages;
    }

    private boolean isNotEmpty(StringSetting setting) {
        String value = setting.getValue();
        return value != null && !value.trim().isEmpty();
    }

    private String generateRandomString(int length) {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}