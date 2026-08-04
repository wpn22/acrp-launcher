# استضافة ملفات ACRP Launcher

## الوضع الحالي

اللانشر يعمل حاليا في وضع تجربة محلية على جهاز التطوير:

```text
http://127.0.0.1:38412
```

اسم الباك ومعرّف السيرفر يجب أن يبقيا:

```text
AcRp
```

وهذا مهم للمودات التي تتحقق من اسم الباك أو مجلد النسخة.

## لماذا GitHub Raw وحده لا يكفي

GitHub يمنع تخزين ملفات أكبر من `100 MiB` في المستودع العادي:

```text
https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github
```

الباك الحالي يحتوي ملفات تتجاوز هذا الحد:

```text
jcef/libcef.dll                                             160.6 MiB
disabled-crashfix/.../ambulance/body.obj                   149.1 MiB
```

كما أن الباك المولد حاليا يقارب `1.70 GiB`. يمكن وضع مثبت اللانشر في GitHub Releases، أما ملفات الباك فمن الأفضل تقديمها عبر تخزين ملفات عام أو CDN يدعم تنزيل كل ملف بواسطة رابط مباشر. GitHub Releases يدعم أصول إصدار يقل كل ملف منها عن `2 GiB`، لكنه يحتاج استراتيجية روابط/رفع مناسبة لآلاف ملفات الباك:

```text
https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
```

## إعداد الإصدار العام

قبل إنشاء نسخة اللاعبين نحتاج:

1. عنوان سيرفر Minecraft الفعلي والمنفذ.
2. رابط استضافة ملفات الباك العام.
3. رابط المتجر، إن وجد.
4. `Discord Application Client ID` إذا أردت حالة اللعب في Discord.
5. تأكيد حذف أو إبقاء مجلد `disabled-crashfix` من التوزيع.

## توليد ملف التوزيع

بعد تعديل الملفات أو عنوان السيرفر:

```powershell
$env:ACRP_FILE_BASE_URL='https://files.example.com/AcRp'
$env:ACRP_SERVER_ADDRESS='play.example.com:25565'
node scripts\generate-acrp-distribution.js
```

السكريبت يولد:

```text
app/assets/distribution.json
docs/acrp_distribution.generated.json
docs/acrp_pack_manifest.json
docs/acrp_forge-14.23.5.2860.version.json
```

إذا لم يوضع عنوان سيرفر فعلي، يبقى الاتصال التلقائي معطلا ولا تعرض الواجهة عنوانا وهميا للاعبين.
