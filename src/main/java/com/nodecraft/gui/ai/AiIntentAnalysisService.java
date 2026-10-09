package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.semantic.NodeDomain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AiIntentAnalysisService {

    private AiIntentAnalysisService() {
    }

    public enum UserIntent {
        GENERATE_NEW,
        MODIFY_PARAM,
        RESTRUCTURE,
        EXPLAIN,
        UNCLEAR
    }

    /**
     * @deprecated Use {@link NodeDomain}. Kept as a binary-compatible alias for older call sites.
     */
    @Deprecated
    public enum DomainTag {
        ARCHITECTURE,
        CURVE,
        PROFILE,
        TERRAIN,
        MATERIAL,
        SDF,
        FIELD,
        ARRAY,
        WORLD,
        MATH,
        DATA_TREE,
        GEOMETRY;

        public NodeDomain toNodeDomain() {
            return NodeDomain.valueOf(name());
        }

        public static DomainTag from(NodeDomain domain) {
            return domain == null ? null : DomainTag.valueOf(domain.name());
        }
    }

    /** Prompt → domain tags (keyword heuristic). Node domains for typeIds come from the catalog. */
    public static Set<NodeDomain> detectDomainTags(String prompt) {
        Set<NodeDomain> tags = EnumSet.noneOf(NodeDomain.class);
        if (prompt == null || prompt.isBlank()) {
            return tags;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);

        if (containsAnyLower(lower,
                "墙", "壁", "窗", "门", "屋顶", "栏杆", "楼梯", "塔", "楼", "建筑",
                "wall", "window", "door", "roof", "railing", "stair", "tower", "building", "architecture",
                "стена", "окно", "дверь", "крыша",
                "muro", "ventana", "puerta", "techo",
                "parede", "janela", "porta", "telhado",
                "mur", "fenêtre", "porte", "toit",
                "wand", "fenster", "tür", "dach")) {
            tags.add(NodeDomain.ARCHITECTURE);
        }
        if (containsAnyLower(lower,
                "曲线", "路径", "曲线阵列", "curve", "path", "spline",
                "крив", "путь", "camino", "caminho", "courbe", "kurve")) {
            tags.add(NodeDomain.CURVE);
        }
        if (containsAnyLower(lower,
                "剖面", "轮廓", "profile", "轮廓线", "polygon profile",
                "профиль", "perfil", "profil")) {
            tags.add(NodeDomain.PROFILE);
        }
        if (containsAnyLower(lower,
                "地形", "山地", "terrain", "heightmap", "landscape",
                "рельеф", "terreno", "terrain", "gelände")) {
            tags.add(NodeDomain.TERRAIN);
        }
        if (containsAnyLower(lower,
                "材质", "方块", "调色", "砖", "苔藓", "风化",
                "material", "palette", "brick", "moss", "weather", "block type",
                "материал", "material", "matériau", "material")) {
            tags.add(NodeDomain.MATERIAL);
        }
        if (containsAnyLower(lower,
                "sdf", "有符号距离", "signed distance")) {
            tags.add(NodeDomain.SDF);
        }
        if (containsAnyLower(lower,
                "场", "标量场", "向量场", "field", "scalar field", "vector field",
                "поле", "campo", "champ", "feld")) {
            tags.add(NodeDomain.FIELD);
        }
        if (containsAnyLower(lower,
                "阵列", "数组", "array", "repeat", "grid",
                "массив", "matriz", "matrice", "anordnung")) {
            tags.add(NodeDomain.ARRAY);
        }
        if (containsAnyLower(lower,
                "世界", "读取", "选区", "world", "region", "selection",
                "мир", "mundo", "monde", "welt")) {
            tags.add(NodeDomain.WORLD);
        }
        if (containsAnyLower(lower,
                "计算", "数学", "加", "减", "乘", "除", "比较",
                "math", "add", "sub", "mul", "div", "logic", "compare",
                "матем", "математи", "matem", "mathématique", "mathe")) {
            tags.add(NodeDomain.MATH);
        }
        if (containsAnyLower(lower,
                "数据树", "列表", "graft", "flatten", "data tree", "list", "tree",
                "дерево", "lista", "arbre", "baum")) {
            tags.add(NodeDomain.DATA_TREE);
        }
        if (containsAnyLower(lower,
                "几何", "球体", "盒子", "方块体", "mesh", "geometry", "sphere", "box", "shape",
                "геометр", "geometr", "géométr", "kugel")) {
            tags.add(NodeDomain.GEOMETRY);
        }
        return tags;
    }

    /**
     * True when the user explicitly asks to write / bake / apply into the Minecraft world.
     */
    public static boolean hasWorldApplyIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        return containsAnyLower(lower,
                "应用到世界", "写入世界", "建造到世界", "放到世界", "烘焙到世界", "直接建到世界",
                "apply changes", "apply to world", "write to world", "build in world", "bake to world",
                "bake into", "place into world", "commit to world",
                "применить к миру", "записать в мир",
                "aplicar al mundo", "escribir en el mundo",
                "aplicar ao mundo", "escrever no mundo",
                "appliquer au monde", "écrire dans le monde",
                "in die welt", "welt schreiben");
    }

    public static String detectInputLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }

        int cjk = 0;
        int cyrillic = 0;
        int hangul = 0;
        int kana = 0;
        int latin = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == null) {
                continue;
            }
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjk++;
            } else if (block == Character.UnicodeBlock.CYRILLIC
                    || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                    || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                    || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                cyrillic++;
            } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                hangul++;
            } else if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) {
                kana++;
            } else if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.LATIN) {
                latin++;
            }
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (cjk > 0 && kana == 0 && hangul == 0) return "zh";
        if (kana > 0) return "ja";
        if (hangul > 0) return "ko";
        if (cyrillic > 0) return "ru";

        if (latin > 0) {
            if (containsAnyLower(lower, " el ", " la ", " los ", " las ", " generar", "jugador", "entidad")) return "es";
            if (containsAnyLower(lower, " não ", "ção", " jogador", " gerar", " entidade")) return "pt";
            if (containsAnyLower(lower, " le ", " la ", " les ", " génér", " joueur", " entité")) return "fr";
            if (containsAnyLower(lower, " der ", " die ", " das ", " und ", " spieler", " entität")) return "de";
            return "en-or-latin";
        }

        return "unknown";
    }

    public static String buildNormalizedIntentPreview(String text) {
        if (text == null || text.isBlank()) {
            return "empty";
        }

        String lower = text.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();

        if (containsAnyLower(lower,
                "生成", "放置", "输出", "烘焙", "spawn", "produce", "output", "create", "bake",
                "создать", "сгенер", "вывод",
                "crear", "generar", "salida",
                "criar", "gerar", "saida",
                "créer", "générer", "sortie",
                "erstellen", "generieren", "ausgabe",
                "出力", "作成",
                "생성", "출력")) {
            tags.add("generate/output");
        }

        if (containsAnyLower(lower,
                "几何", "模型", "球", "圆球", "sphere", "mesh", "geometry", "shape",
                "геометр", "сфера", "форма",
                "geometr", "esfera", "forma",
                "géométr", "sphère", "forme",
                "kugel", "sphäre", "form",
                "幾何", "球体", "形状",
                "기하", "구체", "형상")) {
            tags.add("geometry");
        }

        if (containsAnyLower(lower,
                "位置", "坐标", "头上", "头顶", "上方", "position", "offset", "above", "overhead",
                "позици", "координ", "смещ", "над",
                "posición", "coordenad", "desplaz", "encima",
                "posição", "coordenad", "desloc", "acima",
                "coordonnée", "décalage", "au-dessus",
                "koordinate", "versatz", "oben",
                "座標", "上方",
                "위치", "좌표", "오프셋", "위")) {
            tags.add("spatial/position");
        }

        if (containsAnyLower(lower,
                "玩家", "实体", "生物", "player", "entity", "mob", "living",
                "игрок", "сущност", "моб",
                "jugador", "entidad", "criatura",
                "jogador", "entidade", "criatura",
                "joueur", "entité", "créature",
                "spieler", "entität", "kreatur",
                "プレイヤー", "エンティティ", "モブ",
                "플레이어", "엔티티", "몹")) {
            tags.add("player/entity");
        }

        if (containsAnyLower(lower,
                "颜色", "彩色", "红", "绿", "蓝", "color", "red", "green", "blue", "rgb", "hsv",
                "цвет", "красный", "зеленый", "синий",
                "rojo", "verde", "azul",
                "vermelho", "verde", "azul",
                "couleur", "rouge", "vert", "bleu",
                "farbe", "rot", "grün", "blau",
                "色", "赤", "緑", "青",
                "색", "빨강", "초록", "파랑")) {
            tags.add("color");
        }

        if (containsAnyLower(lower,
                "计算", "数学", "加", "减", "乘", "除", "比较", "math", "add", "sub", "mul", "div", "logic", "compare",
                "матем", "логик", "слож", "вычит", "умнож", "делен", "сравн",
                "matem", "lógica", "sumar", "restar", "multiplicar", "dividir", "comparar",
                "logica", "somar", "subtrair",
                "logique", "addition", "soustraction", "multiplication", "division", "comparer",
                "logik", "addieren", "subtrahieren", "multiplizieren", "dividieren", "vergleichen",
                "数学", "加算", "減算", "乗算", "除算", "比較",
                "수학", "논리", "더하기", "빼기", "곱하기", "나누기", "비교")) {
            tags.add("math/logic");
        }

        if (tags.isEmpty()) {
            return "general-request";
        }
        return String.join(", ", tags);
    }

    public static UserIntent classifyIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return UserIntent.UNCLEAR;
        }

        String lower = prompt.toLowerCase(Locale.ROOT);

        if (containsAnyLower(lower,
                "重构", "重建", "重做", "优化", "替换", "删除", "删掉", "移除", "去掉", "重新连接", "连接已有的",
                "restructure", "rebuild", "reconstruct", "optimize", "replace", "delete", "remove", "disconnect", "reconnect")) {
            return UserIntent.RESTRUCTURE;
        }

        if (containsAnyLower(lower,
                "新建", "生成", "创建", "做一个", "做一", "做个", "添加", "放置", "连接", "连线",
                "帮我", "需要一个", "我想", "造一个", "建一个", "搭一个",
                "generate", "create", "build", "make", "add", "place", "connect")) {
            return UserIntent.GENERATE_NEW;
        }

        if (containsAnyLower(lower,
                "修改", "改成", "改为", "换成", "调整", "增大", "减小", "参数",
                "change", "set", "modify", "update", "adjust", "make it", "increase", "decrease", "parameter")) {
            return UserIntent.MODIFY_PARAM;
        }

        if (containsAnyLower(lower,
                "解释", "说明", "什么", "为什么", "如何", "怎么",
                "explain", "what", "why", "how does", "how do", "how to")) {
            return UserIntent.EXPLAIN;
        }

        return UserIntent.UNCLEAR;
    }

    private static boolean containsAnyLower(String lowerText, String... keywords) {
        if (lowerText == null || lowerText.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
