<p align="center"><img src="./app/assets/images/SealCircle.png" width="150px" height="150px" alt="ACRP Launcher"></p>

<h1 align="center">ACRP Launcher</h1>

<p align="center"><strong>لانشر Adventure City Roleplay</strong></p>

<p align="center">
  <a href="https://github.com/wpn22/acrp-launcher/releases/latest">
    <img src="https://img.shields.io/github/v/release/wpn22/acrp-launcher?style=for-the-badge&color=blue" alt="Last Release">
  </a>
  <a href="https://github.com/wpn22/acrp-launcher/releases">
    <img src="https://img.shields.io/github/downloads/wpn22/acrp-launcher/total?style=for-the-badge&color=green" alt="Downloads">
  </a>
</p>

---

## ما هو ACRP Launcher؟

لانشر مخصص لسيرفر **Adventure City Roleplay**. يتيح لك:

- **تشغيل اللعبة بضغطة زر** بدون تثبيت يدوي لـ Java أو Forge أو المودات
- **تحديث تلقائي** لجميع ملفات اللعبة (المودات، الكونفيق، ريسورس باك)
- **ادارة حسابات متعددة** مع دعم تسجيل دخول Microsoft
- **تنزيل Java الصحيح** تلقائياً إذا لم يكن مثبتاً
- **قائمة قوانين السيرفر** قبل بدء اللعب
- **كتيب القوانين PDF** متاح مباشرة من اللانشر
- **واجهة عربية** بالكامل مع تصميم احترافي

---

## التحميل

حمّل أحدث إصدار من **[GitHub Releases](https://github.com/wpn22/acrp-launcher/releases/latest)**

| المنصة | الملف |
|--------|-------|
| Windows x64 | `ACRP Launcher-setup-VERSION.exe` |

### كيفية التثبيت

1. حمّل ملف التثبيت من الرابط أعلاه
2. اضغط على الملفдвغطتين
3. اتبع خطوات التثبيت
4. افتح اللانشر وسجّل دخولك بحساب Microsoft
5. اضغط **ابدأ اللعب** واترك اللانشر يحمّل الملفات تلقائياً

> **ملاحظة:** قد يظهر لك تحذير Windows SmartScreen لأن البرنامج لم يُوقع رقمياً بعد. اضغط **"المزيد"** ثم **"تشغيل على جهازي"** لتجاوزه.

---

## المميزات

- 🔒 **ادارة حسابات** - سجل دخول بحساب Microsoft وبدّل بين الحسابات بسهولة
- 📂 **تحديث تلقائي** - المودات والملفات تُحمّل وتتحدث تلقائياً
- ☕ **Java تلقائي** - لا تحتاج لتثبيت يدوي لـ Java
- 📰 **اخبار السيرفر** - آخر الأخبار والتحديثات مباشرة من اللانشر
- ⚙️ **إعدادات متقدمة** - تحكم كامل بالإعدادات وخيارات Java
- 🔄 **تحديثات اللانشر نفسه** - اللانشر يتحدث تلقائياً
- 📋 **كتيب القوانين** - اضغط على الزر لعرض كتيب القوانين كاملاً

---

## للمطورين

### المتطلبات

- [Node.js](https://nodejs.org/) v22
- [Git](https://git-scm.com/)

### التثبيت والتشغيل

```bash
git clone https://github.com/wpn22/acrp-launcher.git
cd acrp-launcher
npm install
npm start
```

### بناء المُثبّت

```bash
# Windows
npm run dist:win
```

### هيكل المشروع

```
acrp-launcher/
├── app/
│   ├── assets/
│   │   ├── config/          # محتوى ACRP (חוקים, חדשות, צוות)
│   │   ├── css/             # الأنماط
│   │   ├── js/              # الكود البرمجي
│   │   ├── lang/            # ملفات اللغة
│   │   └── images/          # الصور والخلفيات
│   ├── *.ejs                # صفحات الواجهة
├── scripts/                 # أدوات البناء
├── electron-builder.yml     # إعدادات التثبيت
└── package.json
```

---

## روابط مفيدة

- [GitHub Releases](https://github.com/wpn22/acrp-launcher/releases)
- [Issues](https://github.com/wpn22/acrp-launcher/issues)

---

## اسهام

مرحب بالمساهمات! إذا وجدت مشكلة أو لديك اقتراح، يرجى فتح [Issue](https://github.com/wpn22/acrp-launcher/issues).

---

## الترخيص

مبني على [HeliosLauncher](https://github.com/dscalzi/HeliosLauncher) بترخيص MIT.
