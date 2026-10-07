# CLAUDE.md

Compose Multiplatform uygulaması (Android, iOS, Web). Ortak kod
`shared/src/commonMain/kotlin/com/anksoft/myapplication/`; feature'lar
`features/<feature>/{domain,data,presentation}` katmanlarında, altyapı `core/`
altında. Koin (DI), Ktor (ağ), Voyager (navigasyon, ScreenModel) kullanılır.

## Komutlar

- Unit testler: `./gradlew :shared:testAndroidHostTest` (CI bunu çalıştırır)
- Android build: `./gradlew :androidApp:assembleDebug` (3 flavor; CI bunu çalıştırır). Tek ortam: `assembleDevDebug`
- Web: `./gradlew :webApp:wasmJsBrowserDevelopmentRun -PkmpDemo=true` (ortamlar: `docs/environments.md`)
- UI smoke testleri (Maestro, emülatör/simülatör): bkz. `docs/ui-tests.md`

## Scrum akışı

Her iş (feature, bug, iyileştirme) `.claude/agents/` altındaki rol ajanlarıyla
sırayla ilerler. Her aşamanın çıktısı sprint klasörüne yazılır:
`/mnt/project-files/sprints/<kisa-is-adi>/` varsa orası, yoksa
`docs/sprints/<kisa-is-adi>/`.

| # | Rol | Ajan | Model | Çıktı | Onay |
|---|-----|------|-------|-------|------|
| 1 | Product Owner | `po` | sonnet | `01-story.md`: user story, Given/When/Then kabul kriterleri, öncelik | Kullanıcı onayı |
| 2 | Teknik Analist | `tech-analyst` | opus | `02-analysis.md`: etkilenen modüller, veri akışı, API, riskler | |
| 3 | Senior Developer | `senior-dev` | opus | `03-plan.md`: dosya bazında plan, test stratejisi, alt görevler | Kullanıcı onayı |
| 4 | Junior Developer | `junior-dev` | sonnet | Draft PR + `04-implementation.md`; `senior-dev` PR'ı review edip merge eder | |
| 5 | QA | `qa` | sonnet | `05-qa-report.md`: AC bazında test sonuçları; hata varsa 4'e döner | |

Backend işlerinde 3. ve 4. aşamalarda backend rolleri devreye girer:

| # | Rol | Ajan | Model | Çıktı | Onay |
|---|-----|------|-------|-------|------|
| 3 | Backend Architect | `backend-architect` | opus | `03-plan.md` backend bölümü: mimari kararlar (ADR), API sözleşmesi, alt görevler (`B1`, ...) | Kullanıcı onayı |
| 4 | Junior Backend Developer | `junior-backend-dev` | sonnet | Draft PR + `04-implementation.md`; `backend-architect` PR'ı review edip merge eder | |

İş hem istemci hem backend içeriyorsa `senior-dev` istemci, `backend-architect`
backend bölümünü aynı `03-plan.md`'de yazar; API sözleşmesini birlikte
belirlerler. Backend kodu bu repoda değil, ayrı `alianikaydin/KmpDemoBackend`
reposundadır; API sözleşmesi bu repoya, backend reposundan yayımlanan sürümlü
`com.anksoft.kmpdemo:contract` kütüphanesiyle gelir (Ali'nin kararı, 2026-10-02).

Model: Her ajanın modeli kendi dosyasındaki `model:` satırıyla belirlenir
(`.claude/agents/<ajan>.md`). Değerler: `opus`, `sonnet`, `haiku`, tam model
ID'si veya ana oturumun modelini kullanmak için `inherit`. Karar ve review
gerektiren roller (analist, senior) daha güçlü modelde, uygulama ve test
rolleri daha ekonomik modelde çalışır.

Kurallar:

- 1 ve 3 sonrası kullanıcı onayı olmadan bir sonraki aşamaya geçilmez.
- Junior'ın PR'ını `senior-dev` (backend PR'larını `backend-architect`) review eder ve şu üç koşul sağlanınca
  squash-merge eder: CI yeşil, QA raporu geçti, açık review bulgusu yok.
  Karar veremediği bir durum olursa (ör. AC yorumu, kapsam dışı değişiklik,
  geçmeyen ama nedeni belirsiz test) merge etmez, kullanıcıya sorar.
- Kullanıcıyla ve sprint dokümanlarında Türkçe yazılır; kod, yorumlar, commit
  mesajları ve PR metinleri İngilizcedir ve Android (Kotlin/Compose) ile iOS
  (Swift/SwiftUI) kodlama kurallarına uyar: `docs/guidelines/android.md`,
  `docs/guidelines/ios.md`.
