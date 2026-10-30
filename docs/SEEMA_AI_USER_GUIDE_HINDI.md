# Seema AI — पूरी गाइड (हिन्दी)

यह गाइड v1.8 के लिए है: ऐप में कौन-सी चीज़ कहाँ है, उसे चालू करने से क्या होता है, और कौन-सा AI मॉडल चुनें।

---

## 1. सबसे पहले ये 5 सेटिंग कर लीजिए

1. **Settings → AI provider:** Groq और/या Gemini की key डालिए → **Test** दबाइए। हरा "connected" दिखना चाहिए।
2. **Settings → Voice → Voice mode:** **"Always listening (app open)"** चुनिए। ऐप खुलते ही Seema सुनने लगेगी। (v1.8 में यही default है।)
3. **Settings → Voice → Language:** **Auto (हिन्दी / বাংলা)** रखिए।
4. **Home पर "Safety Mode on" की पट्टी दिखे तो "Turn off" दबाइए।** Safety Mode में call, WhatsApp, SMS, ऐप खोलना और गाना बंद रहते हैं।
5. **फ़ोन की Settings → Accessibility → Visibility enhancements → "Remove animations" OFF** रखिए। वरना Arc Reactor और 3D Core रुक जाते हैं।

---

## 2. कौन-सा AI ("दिमाग़") इस्तेमाल करें

| ज़रूरत | सुझाव | क्यों |
|---|---|---|
| **मुख्य दिमाग़ (Hindi/Bengali अच्छी, तेज़, free)** | **Gemini → `gemini-2.5-flash`** (या `gemini-2.5-flash-lite`, जो और तेज़ है) | हिन्दी और বাংলা अच्छी समझता है। ऐप उसकी "लंबी सोच" बंद रखता है, इसलिए जवाब जल्दी आता है। |
| **सबसे तेज़ जवाब** | **Groq → `llama-3.3-70b-versatile`** | Groq बहुत तेज़ है। यह मॉडल "सोचने वाला" नहीं है, इसलिए सीधे जवाब देता है। |
| **बहुत ही तेज़, थोड़ा कम समझदार** | Groq → `llama-3.1-8b-instant` | छोटे सवाल और commands के लिए। |
| `openai/gpt-oss-120b` (Groq) | काम करता है, पर ऊपर वाले से धीमा | यह reasoning मॉडल है। ऐप इसे "low" सोच पर चलाता है। |

**दोनों key डालिए।** जो AI चुना है वह fail हो (limit ख़त्म, server busy), तो ऐप अपने-आप दूसरे AI से जवाब ले लेता है और चैट में बता देता है ("answered by Groq instead")।

Free limits कंपनियाँ बदलती रहती हैं। अगर "Quota exceeded" दिखे, तो दूसरा AI चुन लीजिए।

---

## 3. आवाज़ (Voice)

| सेटिंग (Settings → Voice) | क्या करती है |
|---|---|
| **Speech output** (Sarvam / ElevenLabs / Cartesia / Deepgram / Android) | Seema की आवाज़। जो चुना है वह fail हो तो अगली आवाज़ अपने-आप चलती है। Android वाली आवाज़ बिना इंटरनेट भी चलती है। |
| **Speaking speed / Voice pitch** | रफ़्तार और भारीपन। Pitch सिर्फ़ Android आवाज़ पर लगता है। |
| **Language** | Auto = आप जिस भाषा में बोलें, उसी में जवाब। Hinglish को हिन्दी माना जाता है। |
| **Speak replies aloud** | जवाब बोलकर सुनाए। Chat में 🔊 बटन से भी चालू/बंद होता है। |
| **Voice mode** | **Always listening** = ऐप खुलते ही सुनती है। चुप रहने पर सो जाती है और "सीमा" बोलने पर जागती है। **Conversation session** = Mic एक बार दबाइए, फिर बात चलती रहती है। **Push-to-talk** = हर बार Mic दबाना पड़ता है (सबसे कम बैटरी)। Home पर **"Mode: … ⇄"** टैग दबाने से भी बदलता है। |
| **Interrupt by voice** | Seema बोल रही हो और आप बीच में बोलें तो वह रुक जाती है। **सिर्फ़ तब चलता है जब Deepgram key सेव हो और Always listening या Conversation session चालू हो**, क्योंकि तभी फ़ोन का echo-canceller चलता है। बाक़ी हालत में वह पूरा जवाब बोलती है, ताकि अपनी ही आवाज़ सुनकर न रुके। रोकना हो तो core पर टैप कीजिए या Stop दबाइए। |
| **Speech input** (Android / Deepgram) | सुनने वाला engine। Deepgram Hinglish बेहतर समझता है और बिना beep के सुनता है, पर key चाहिए। বাংলা हमेशा Android recogniser से सुनी जाती है। |
| **Sleep mode** | इतने सेकंड चुप रहने पर Seema सो जाती है। फिर "सीमा" बोलकर जगाइए। |

**Bengali/Hindi आवाज़ फ़ोन पर:** Settings → Status में "Android voice (Hindi)" और "(বাংলা)" दिखते हैं। "Install" दबाकर फ़ोन की आवाज़ डाउनलोड कर सकते हैं।

---

## 4. Home स्क्रीन

- **🎨 टैग:** Arc Reactor ⇄ 3D Core (एक टैप में बदलता है)। Settings में सबसे ऊपर भी है।
- **Mode टैग:** Always listening ⇄ Tap to talk।
- **3D Core पेज पर:**
  - घड़ी।
  - असली मौसम: पहली बार टैप करने पर location की अनुमति माँगता है।
  - Battery, RAM, Storage।
  - Wi-Fi, Bluetooth, Torch, Camera, Phone settings: इन्हें देखने के लिए tiles वाली पट्टी को side में swipe कीजिए।
- **नीचे का dock:** Chat, Mic (बीच में, घूमती रिंग), Stop, Settings।
- **Core पर टैप:** बोलना रोकती है, या सोई हो तो जगाती है।

---

## 5. Chat

- 🔍 पुरानी सारी बातचीत में खोजें।
- 🔊 / 🔇 जवाब बोलना चालू/बंद।
- ➕ नई बातचीत (पुरानी सेव रहती है)।
- हर जवाब के नीचे **Copy** और **🔊 Repeat**।
- गलती आए तो **Retry**।

---

## 6. Settings में बाक़ी चीज़ें

| जगह | काम |
|---|---|
| **Knowledge Base** | PDF/DOCX/TXT/MD डालिए। Seema उनमें से जवाब देती है और `[Doc: नाम]` बताती है। स्कैन की हुई (फ़ोटो वाली) PDF नहीं पढ़ी जाती। |
| **Memory & privacy** | याद रखी बातें चालू/बंद, चैट history, Export/Import। एक-एक memory बदलने के लिए **Tools → Memory** खोलिए। |
| **Phone & music** | Direct calling, YouTube Music/Spotify। |
| **Notifications & WhatsApp** | WhatsApp मैसेज आने पर बताना (Notification access चाहिए)। |
| **Incoming calls** | कौन call कर रहा है, यह बोलना (call-screening role चाहिए)। |
| **Assistant control** | Safety Mode (banking के समय), Pause। |
| **Performance & Battery** | **"Minimal animation" OFF रखें**, वरना core रुक जाता है। Smart power सिर्फ़ धीमा करता है, रोकता नहीं। |

**Tools पेज:**
- Khata (ख़र्चा)
- PNR diary
- Memory
- Voice diagnostics: आवाज़ की दिक्कत यहाँ दिखती है
- Action history: कौन-सा काम हुआ या नहीं

---

## 7. क्या इंटरनेट चाहिए?

- **चाहिए:**
  - AI जवाब
  - web search
  - मौसम
  - cloud आवाज़ें (Sarvam, ElevenLabs, Cartesia, Deepgram)
- **बिना इंटरनेट चलता है:**
  - दोनों core और घड़ी
  - khata, PNR
  - memories, documents
  - Android आवाज़ और Android recogniser, अगर उनकी offline language फ़ाइल डाउनलोड हो

---

## 8. दिक्कत आए तो

| दिक्कत | उपाय |
|---|---|
| Core हिल नहीं रहा | फ़ोन में "Remove animations" OFF करें, और ऐप में "Minimal animation" OFF करें |
| WhatsApp या call नहीं गया | Safety Mode बंद करें। Action history देखें। |
| हर बार Mic दबाना पड़ता है | Voice mode → Always listening, या Home पर Mode टैग दबाएँ |
| जवाब धीमा | Groq `llama-3.3-70b-versatile` या Gemini `2.5-flash` चुनें |
| आवाज़ नहीं आई | Tools → Voice diagnostics देखें, या Settings → Voice → Test voice दबाएँ |
| "कुछ सुनाई नहीं दिया" | पास से साफ़ बोलें। Deepgram key से Hinglish बेहतर सुनी जाती है। |
