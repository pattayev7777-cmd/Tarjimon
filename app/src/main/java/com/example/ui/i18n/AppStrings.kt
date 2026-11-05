package com.example.ui.i18n

import com.example.data.model.AppLanguage

data class LocalizedUiStrings(
    val headline: String,
    val subtitle: String,
    val urlPlaceholder: String,
    val translateButton: String,
    val navTranslate: String,
    val navHistory: String,
    val navSubscription: String,
    val navSupport: String,
    val navAccount: String,
    val navAdmin: String,
    val freeRemainingLabel: String,
    val proActiveLabel: String,
    val selectTargetLanguage: String,
    val photoAnalysisTitle: String,
    val photoAnalysisSubtitle: String
)

object AppStrings {
    fun forLanguage(lang: AppLanguage): LocalizedUiStrings = when (lang) {
        AppLanguage.EN -> LocalizedUiStrings(
            headline = "Translate YouTube Videos Into Any Language With AI",
            subtitle = "Professional AI-powered subtitles in Uzbek, English, Russian, and Arabic.",
            urlPlaceholder = "Paste your YouTube video link here...",
            translateButton = "TRANSLATE VIDEO",
            navTranslate = "Translate",
            navHistory = "Translations",
            navSubscription = "Subscription",
            navSupport = "Contact Admin",
            navAccount = "My Account",
            navAdmin = "Admin",
            freeRemainingLabel = "Free Translations Remaining",
            proActiveLabel = "Pro Subscription Active",
            selectTargetLanguage = "Select Target Subtitle Language",
            photoAnalysisTitle = "AI Photo & Frame Analyzer (Gemini 4 Argon)",
            photoAnalysisSubtitle = "Upload a photo, subtitle screenshot, or document to extract and translate text with high-thinking AI."
        )
        AppLanguage.UZ -> LocalizedUiStrings(
            headline = "Translate YouTube Videos Into Any Language With AI",
            subtitle = "O‘zbek, Ingliz, Rus va Arab tillarida professional sun’iy intellekt subtitrlari.",
            urlPlaceholder = "Paste your YouTube video link here...",
            translateButton = "TRANSLATE VIDEO",
            navTranslate = "Tarjima",
            navHistory = "Tarix",
            navSubscription = "Obuna",
            navSupport = "Admin aloqa",
            navAccount = "Kabinet",
            navAdmin = "Admin",
            freeRemainingLabel = "Qolgan bepul tarjimalar",
            proActiveLabel = "Faol Pro obuna",
            selectTargetLanguage = "Maqsadli subtitr tilini tanlang",
            photoAnalysisTitle = "AI Rasm va Kadr Tahlili (Gemini 4 Argon)",
            photoAnalysisSubtitle = "Rasm, subtitr skrinshoti yoki chekni yuklang va yuqori tafakkurli AI yordamida tahlil qiling."
        )
        AppLanguage.RU -> LocalizedUiStrings(
            headline = "Translate YouTube Videos Into Any Language With AI",
            subtitle = "Профессиональные ИИ-субтитры на узбекском, английском, русском и арабском языках.",
            urlPlaceholder = "Paste your YouTube video link here...",
            translateButton = "TRANSLATE VIDEO",
            navTranslate = "Перевод",
            navHistory = "История",
            navSubscription = "Подписка",
            navSupport = "Связь с админом",
            navAccount = "Аккаунт",
            navAdmin = "Админ",
            freeRemainingLabel = "Осталось бесплатных переводов",
            proActiveLabel = "Активна Pro подписка",
            selectTargetLanguage = "Выберите целевой язык субтитров",
            photoAnalysisTitle = "ИИ-анализ фото и кадров (Gemini 4 Argon)",
            photoAnalysisSubtitle = "Загрузите фото, скриншот субтитров или документ для глубокого анализа и перевода."
        )
        AppLanguage.AR -> LocalizedUiStrings(
            headline = "Translate YouTube Videos Into Any Language With AI",
            subtitle = "ترجمة احترافية بالذكاء الاصطناعي باللغات الأوزبكية والإنجليزية والروسية والعربية.",
            urlPlaceholder = "Paste your YouTube video link here...",
            translateButton = "TRANSLATE VIDEO",
            navTranslate = "ترجمة",
            navHistory = "السجل",
            navSubscription = "الاشتراك",
            navSupport = "تواصل مع المسؤول",
            navAccount = "حسابي",
            navAdmin = "الإدارة",
            freeRemainingLabel = "الترجمات المجانية المتبقية",
            proActiveLabel = "اشتراك Pro نشط",
            selectTargetLanguage = "اختر لغة الترجمة المستهدفة",
            photoAnalysisTitle = "تحليل الصور بالذكاء الاصطناعي (Gemini 4 Argon)",
            photoAnalysisSubtitle = "ارفع صورة أو لقطة شاشة لتحليلها وترجمتها بدقة عالية."
        )
    }
}
