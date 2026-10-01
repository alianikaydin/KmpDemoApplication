# CLAUDE.md

Compose Multiplatform uygulaması (Android, iOS, Web). Ortak kod
`shared/src/commonMain/kotlin/com/anksoft/myapplication/`; feature'lar
`features/<feature>/{domain,data,presentation}` katmanlarında, altyapı `core/`
altında. Koin (DI), Ktor (ağ), Voyager (navigasyon, ScreenModel) kullanılır.

## Komutlar

- Unit testler: `./gradlew :shared:testAndroidHostTest` (CI bunu çalıştırır)
- Android build: `./gradlew :androidApp:assembleDebug` (CI bunu çalıştırır)
- Web: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`

## Scrum akışı

Her iş (feature, bug, iyileştirme) `.claude/agents/` altındaki rol ajanlarıyla
sırayla ilerler. Her aşamanın çıktısı sprint klasörüne yazılır:
`/mnt/project-files/sprints/<kisa-is-adi>/` varsa orası, yoksa
`docs/sprints/<kisa-is-adi>/`.

| # | Rol | Ajan | Çıktı | Onay |
|---|-----|------|-------|------|
| 1 | Product Owner | `po` | `01-story.md`: user story, Given/When/Then kabul kriterleri, öncelik | Kullanıcı onayı |
| 2 | Teknik Analist | `tech-analyst` | `02-analysis.md`: etkilenen modüller, veri akışı, API, riskler | |
| 3 | Senior Developer | `senior-dev` | `03-plan.md`: dosya bazında plan, test stratejisi, alt görevler | Kullanıcı onayı |
| 4 | Junior Developer | `junior-dev` | Draft PR + `04-implementation.md`; `senior-dev` PR'ı review eder | |
| 5 | QA | `qa` | `05-qa-report.md`: AC bazında test sonuçları; hata varsa 4'e döner | |

Kurallar:

- 1 ve 3 sonrası kullanıcı onayı olmadan bir sonraki aşamaya geçilmez.
- PR'ı merge etmek kullanıcıya aittir.
- Kullanıcıyla ve sprint dokümanlarında Türkçe yazılır; kod, yorumlar, commit
  mesajları ve PR metinleri İngilizcedir ve Android (Kotlin/Compose) ile iOS
  (Swift/SwiftUI) kodlama kurallarına uyar.
