# ACRP Launcher Content

ملف التحكم السهل في محتوى اللانشر:

```text
%APPDATA%\ACRP Launcher\acrp-content.json
```

يتم إنشاء الملف تلقائيا عند تشغيل اللانشر. عدل الملف ثم أعد تشغيل اللانشر.

أهم الحقول:

- `brand`: اسم اللانشر والسيرفر واسم الباك.
- `server`: عنوان السيرفر، إصدار ماينكرافت، واللودر.
- `home`: نصوص الصفحة الرئيسية.
- `links`: روابط Discord و YouTube والموقع.
- `gallery`: صور Adventure City التي تظهر في الصفحة الرئيسية.
- `store`: رابط المتجر والمنتجات المعروضة.
- `news`: الأخبار التي تظهر في اللانشر.
- `rules`: القوانين.
- `staff`: أسماء فريق الإدارة.
- `discordRpc`: لا تضع `clientId` إلا بعد إنشاء تطبيق Discord رسمي للسيرفر.
- `game`: إعدادات تشغيل اللعبة مثل تفعيل الريسورس باك تلقائيا.

مثال خبر:

```json
{
  "title": "افتتاح Adventure City",
  "summary": "تم تجهيز النسخة التجريبية الأولى.",
  "date": "2026-05-23",
  "author": "Adventure City",
  "image": "assets/images/backgrounds/acrp-hero.png",
  "link": "https://discord.gg/cEDuKtcBk6",
  "content": "<p>حياكم الله في Adventure City. تابعوا الديسكورد لمعرفة كل جديد.</p>"
}
```

ضع الخبر داخل مصفوفة `news`:

```json
"news": [
  {
    "title": "افتتاح Adventure City",
    "summary": "تم تجهيز النسخة التجريبية الأولى.",
    "date": "2026-05-23",
    "author": "Adventure City",
    "image": "assets/images/backgrounds/acrp-hero.png",
    "link": "https://discord.gg/cEDuKtcBk6",
    "content": "<p>حياكم الله في Adventure City.</p>"
  }
]
```

مثال فريق الإدارة:

```json
"staff": [
  {
    "name": "Yousef",
    "role": "Owner"
  }
]
```

طريقة المتجر:

الأفضل أن يكون الدفع والتسليم في متجر خارجي مثل Tebex أو CraftingStore. اللانشر يعرض المنتجات فقط ويفتح رابط المتجر أو رابط المنتج.

مثال متجر:

```json
"store": {
  "url": "https://store.example.com",
  "note": "AC هي عملة السيرفر. روابط الشراء تتفعل بعد ربط المتجر الرسمي.",
  "items": [
    {
      "title": "5,000,000 AC",
      "description": "عرض قوي: خمسة مليون AC مع نصف مليون إضافية مجانا.",
      "price": "500$",
      "tag": "Best Offer",
      "bonus": "+500,000 AC مجانا",
      "url": "https://store.example.com/package/ac-5m"
    }
  ]
}
```

إذا كان `store.url` فاضي يبقى زر فتح المتجر مخفي. وإذا كان `item.url` فاضي تظهر البطاقة كمنتج قادم بدون رابط.

تسعيرة AC الحالية:

- `100,000 AC = 10$`
- `1,000,000 AC = 100$`
- `5,000,000 AC = 500$` مع `500,000 AC` مجانا
- `10,000,000 AC = 1000$` مع بونص قابل للتعديل

ربط Discord:

روابط الدعوة تعدل من:

```json
"links": {
  "discord": "https://discord.gg/cEDuKtcBk6"
}
```

أما Discord Rich Presence يحتاج تطبيق رسمي من Discord Developer Portal. بعد إنشاء التطبيق، انسخ `Application ID` وضعه هنا:

```json
"discordRpc": {
  "clientId": "ضع Application ID هنا",
  "largeImageKey": "acrp",
  "largeImageText": "Adventure City Roleplay"
}
```

إذا بقي `clientId` فاضي، اللانشر يفتح روابط الديسكورد طبيعي لكن لا يشغل Rich Presence.

تفعيل الريسورس باك تلقائيا:

```json
"game": {
  "forceResourcePacks": true,
  "resourcePacks": [
    "cocricot_1.12.2_v1.11.0.zip"
  ]
}
```

عند الضغط على تشغيل، اللانشر يكتب الريسورس باك في `options.txt` داخل مجلد نسخة `AcRp`. إذا كان `forceResourcePacks` يساوي `true` يستبدل قائمة الريسورس باكات بالقائمة الرسمية للسيرفر.

مثال صور الصفحة الرئيسية:

```json
"gallery": [
  {
    "image": "assets/images/backgrounds/acrp-hero.png",
    "label": "Adventure City"
  }
]
```

للتعديل من حسابك في GitHub:

1. ارفع نسخة من `acrp-content.json` في مستودع GitHub.
2. افتح الملف من GitHub بصيغة Raw.
3. انسخ رابط Raw وضعه في `remoteContentUrl`.
4. أي تعديل لاحق في ملف GitHub يظهر بعد إعادة تشغيل اللانشر.
