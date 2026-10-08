# ⬛ Fris Black v2 — automação atômica (sem root)

Fluxo: **Escolher app → colar/escrever script → validar → ativar → roda sozinho, em sequência, até o fim.**

## O que melhorou (pesquisa GitHub/internet)

Baseado nos melhores open-source de automação Android:

| Projeto | Stars | O que aproveitamos |
|---|---|---|
| `SuperMonster003/AutoJs6` | 6k+ | Seletores por texto/id (estilo UiAutomator), `dispatchGesture` human-like, gravação |
| `xjunz/AutoTask` | — | Dual engine Shizuku + Accessibility, `UiAutomation`, baixo consumo (event-driven) |
| `SunZhi-Will/OpenRing` | — | Executor RPA atômico no celular, sem PC/root, CI com APK em Artifacts |
| `Autonion/Automation-Companion` | — | Triggers (tempo/local/wifi/app), debugger com log por passo, offline-first |
| `PortableDiag/GhostUser` | — | 2 motores (root/acessibilidade) + painel flutuante que só aparece se o usuário pedir |
| `TonyJiangWJ/AutoScriptBase` | 600+ | Fila anti-disputa, OCR, `Automator` que escolhe root ou acessibilidade sozinho |
| `RikkaApps/Shizuku` | — | API privilegiada via ADB sem root (nível 3, opcional futuro) |

## Níveis de poder (honesto)

| Nível | Permissão | Faz | Risco |
|---|---|---|---|
| 1 — Base | nenhuma | abrir app, aviso, cronômetro, agendar, bolha, wifi/BT/lanterna/volume | baixo |
| 2 — Acessibilidade | usuário liga em Ajustes | clicar, digitar, rolar, ler botões em qualquer app | médio |
| 3 — Shizuku | Shizuku + ADB | UiAutomation sem toggle de acessibilidade | médio-alto (futuro) |
| 4 — Root | root | tudo via `input tap/swipe` | alto |

> Sem root, o Android bloqueia de propósito: banco/pagamento/telas `FLAG_SECURE` rejeitam gesto sintético.
> Isso é segurança do aparelho, não bug. Nível 1+2 cobre ~90% dos apps normais.

## FrisScript v2 (PT-BR)

```text
ALVO YouTube

QUANDO chegar às 23:00
SE for depois das 22:00
ENTÃO abrir YouTube
ENTÃO esperar até aparecer "Pesquisar" em 10s
ENTÃO clicar em "Pesquisar"
ENTÃO digitar "música para dormir"
ENTÃO clicar em "Pesquisar"
ENTÃO esperar 3 segundos
ENTÃO rolar para baixo
ENTÃO mostrar aviso "Modo noite ativado"
ENTÃO registrar "modo noite ok"
```

Gatilhos: `abrir <app>` · `chegar às HH:MM` · `ligar o celular` · `receber notificação de <app>` ·
`conectar wifi` · `bateria abaixo de N%` · manual.
Ações UI: `clicar em "texto"` · `clicar por id "x"` · `clicar em coordenada x, y` (evite) ·
`digitar` · `limpar texto` · `rolar/deslizar` · `esperar N segundos` ·
`esperar até aparecer/desaparecer` · `voltar` · `fechar teclado` · `confirmar "…?"`.
Sistema: `mostrar aviso/erro` · `registrar` · `cronômetro` · `screenshot` · `bloquear tela` ·
`wifi/bluetooth/lanterna/volume/brilho` · `abrir url`.
Controle: `parar script` · `continuar mesmo se falhar` · `repetir bloco até N vezes` (máx 5).

### Trava de segurança
- Validação antes de ativar, erro com linha (`linha 7: …`).
- Timeout por passo (12s) e total (60s). `esperar até` padrão 10s, máx 30s.
- Máx 30 passos, 20 rodadas/min, repetição máx 5x.
- Ação sensível (`enviar/apagar/comprar/pagar/pix/senha/…`) **exige `CONFIRMAR` antes** — senão nem valida.
- Cada rodada tem ID único (sem duplicar efeito). Tudo vai pro Histórico.
- Bolha: pausar/continuar/abrir painel. Rate-limit + `performGlobalAction` seguro.

## Como pegar o APK

1. **GitHub Actions (recomendado):** aba `Actions` → `Fris Black APK` → run verde →
   `Artifacts` → `fris-black-debug-apk` → extrai o `.apk` → instala no celular.
2. **Local:** `cd fris-black && gradle :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar e usar
1. Instala o APK (permite "fontes desconhecidas").
2. Abre **Fris Black** → `Ativar Acessibilidade` → liga **Fris Black** em Ajustes.
3. (Opcional) permite sobrepor apps → liga a bolha.
4. `Carregar exemplos` ou `Novo script` → escolhe o app no campo (vira `ALVO`) →
   `Validar` → `Salvar + Ativar` → `▶ Testar agora`.
5. `Histórico` mostra cada passo com ID da rodada.

## Estrutura
```
fris-black/
  app/src/main/java/com/fris/black/
    script/FrisScript.kt        # parser + validador (erro por linha)
    engine/ScriptExecutor.kt    # executor atômico (timeout/limite/histórico)
    engine/AndroidUiDriver.kt   # Accessibility → ações reais
    engine/Scheduler.kt         # AlarmManager + BOOT + rate-limit
    service/FrisAccessibilityService.kt
    overlay/BubbleService.kt
    data/AppResolver.kt, FrisStore.kt
    ui/MainActivity.kt, EditorActivity.kt, HistoryActivity.kt
  app/src/test/                 # 11 testes unitários (parser + executor)
```

Testes: `cd fris-black && gradle :app:testDebugUnitTest` — 11/11 passando.
APK: `gradle :app:assembleDebug` — `com.fris.black.debug` v2.0.0, minSdk 26 (Android 8+), 6.7MB.

## Limites conhecidos (sem promessa falsa)
- Não cruza `FLAG_SECURE`/senha de banco/sistema — nem com Acessibilidade.
- Coordenada quebra entre aparelhos — prefira `clicar em "texto"`.
- Doze/OEM (Xiaomi/Samsung) pode atrasar alarme — fixe com "sem restrição de bateria".
- Shizuku/Root são fases futuras (interfaces já isoladas em `UiDriver`).
