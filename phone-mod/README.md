<h1 align="center">ACRP Phone</h1>

<p align="center"><strong>جوال Adventure City Roleplay — ماينكرافت 1.12.2 Forge</strong></p>

---

## نظرة عامة

مود جوال داخل اللعبة لسيرفر **Adventure City Roleplay**: رسائل ومكالمات
صوتية وتطبيقات، مبني من الصفر مع **محرك نصوص عربية حقيقي**.

الفرق الأساسي عن مودات الجوال الأخرى (مثل SPhone) أن ماينكرافت 1.12.2
**لا يستطيع رسم العربي إطلاقاً** — لا يصل الحروف ولا يدعم الاتجاه من
اليمين لليسار. هذا المود يحل المشكلة بمحرك خطوط مستقل.

---

## حالة المشروع

| المرحلة | الحالة |
|---|---|
| 0 — تجهيز المشروع والبناء | ✅ |
| 1 — الهيكل العظمي (الغرض + الشاشة) | ⏳ |
| **2 — محرك النصوص العربية** | ✅ **الأساس جاهز ومختبر** |
| 3 — محرك الواجهة | ⏳ |
| 4 — النواة والشبكة والتخزين | ⏳ |
| 5 — الرسائل | ⏳ |
| 6 — المكالمات (Simple Voice Chat) | ⏳ |
| 7 — بقية التطبيقات | ⏳ |
| 8 — واجهة الإضافات (API) | ⏳ |
| 9 — التلميع | ⏳ |
| 10 — الشحن إلى اللانشر | ⏳ |

---

## محرك النصوص العربية

### المشكلة

الحل البديهي هو تمرير النص كامل لـ`Font.layoutGlyphVector` مع
`LAYOUT_RIGHT_TO_LEFT`. يبدو أنه يعمل — لكنه **يعكس الأرقام والحروف
اللاتينية المدمجة**:

| النص | نتيجة الطريقة البديهية | الصحيح |
|---|---|---|
| `رقمي هو 0501234567` | `7654321050` ❌ | `0501234567` ✅ |
| `من 100 إلى 250 ريال` | `001 … 052` ❌ | `100 … 250` ✅ |
| `مرحبا ACRP اهلا` | `PRCA` ❌ | `ACRP` ✅ |

رقم جوال معكوس في سيرفر رول بلاي = عطل حقيقي، لأن اللاعبين ينسخون
الأرقام من الشاشة.

### الحل — تمريرتان

1. **تقسيم ثنائي الاتجاه**: `java.text.Bidi` يقسّم النص إلى runs
   اتجاهية ويعطي كل واحد مستوى (level).
2. **إعادة ترتيب بصري**: `Bidi.reorderVisually` يرتب الـruns حسب ظهورها
   على الشاشة.
3. **تشكيل كل run باتجاهه**: كل run يُمرَّر لـ`layoutGlyphVector` باتجاهه
   هو — العربي RTL والأرقام LTR.
4. **الحفاظ على السياق**: كل run يُخطَّط مقابل **مصفوفة الحروف الكاملة**
   مع تمرير `start`/`limit` فقط، حتى يرى المُشكِّل الحروف المجاورة وتبقى
   الحروف موصولة عبر حدود الـrun.

النتيجة `ShapedText`: قائمة جليفات مرتّبة بصرياً (يسار ← يمين) وجاهزة
للرسم مباشرة.

> **مهم:** `ShapedGlyph.glyphCode` هو **رقم الجليف في الخط** وليس
> codepoint. الحرف العربي الواحد له أشكال مختلفة (أول/وسط/آخر/منفصل)
> بأرقام جليف مختلفة — وهذا بالضبط ما يجعل الحروف تتصل. أطلس الجليفات
> يجب أن يُفهرس على `glyphCode`.

### إثبات بصري

```bash
java -cp build/classes:build/testclasses:... \
     com.acrp.phone.client.text.TextRenderProof proof.png
```

يرسم كل جملة مرتين — بالطريقة البديهية وبالمحرك — للمقارنة بالعين بدون
تشغيل ماينكرافت. الـCI يرفع الصورة كـartifact في كل build.

---

## البناء

**المتطلبات:** JDK **8** (إجباري لـForge 1.12.2) — الـwrapper يجلب
Gradle 4.10.3 تلقائياً.

```bash
./gradlew build        # يبني الجار
./gradlew test         # اختبارات محرك النص
./gradlew runClient    # تشغيل كلاينت تطويري
./gradlew runServer    # تشغيل سيرفر تطويري
```

### اختبار محرك النص بدون Forge

محرك النص لا يعتمد على ماينكرافت، فيمكن اختباره في ثوانٍ:

```bash
mkdir -p build/classes build/testclasses build/testlibs
curl -sSfL -o build/testlibs/junit.jar \
  https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
curl -sSfL -o build/testlibs/hamcrest.jar \
  https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

javac -encoding UTF-8 -d build/classes \
  $(find src/main/java/com/acrp/phone/client/text -name '*.java')
javac -encoding UTF-8 -cp "build/classes:build/testlibs/*" \
  -d build/testclasses $(find src/test/java -name '*.java')
java -cp "build/classes:build/testclasses:build/testlibs/junit.jar:build/testlibs/hamcrest.jar" \
  org.junit.runner.JUnitCore com.acrp.phone.client.text.TextShaperTest
```

---

## الإصدارات المثبّتة

| البند | الإصدار | السبب |
|---|---|---|
| Minecraft | 1.12.2 | يطابق سيرفر ACRP |
| Forge | 14.23.5.2860 | **نفس بناء السيرفر بالضبط** |
| Java | 8 | إجباري لـ1.12.2 |
| Gradle | 4.10.3 | آخر إصدار يقبله ForgeGradle 3 |
| Mappings | `stable_39-1.12` | |
| Mod ID | `acrpphone` | مختلف عمداً عن `sphone` و`acphone` تفادياً للتصادم |

---

## الخط

المحرك يبحث عن خط عربي بهذا الترتيب:

1. `src/main/resources/assets/acrpphone/fonts/phone.ttf` (المرفق مع المود)
2. خطوط النظام (DejaVu Sans / Noto Sans Arabic) — للاختبارات فقط

**قبل الإطلاق يجب إرفاق خط عربي برخصة مفتوحة** (مثل Noto Sans Arabic أو
Cairo برخصة OFL) في المسار أعلاه.

---

## ملاحظة ترخيص

هذا المود مكتوب **من الصفر**. لا يحتوي على أي كود أو أصول من SPhone أو
غيره.
