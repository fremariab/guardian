# Project Information

**Project Title:**  
**Guardian** – Enhancing Ride-Hailing Passenger Safety and Comfort for Women in Ghana Using Artificial Intelligence  

**Authors:**  
Beryl Awurama Ayaw Koram (B.Sc. MIS)  
Freda-Marie Beecham (B.Sc. CS)  

**Institution:**  
Ashesi University (2025)

---

## Project Summary

This project focuses on addressing the safety concerns that women face when using ride-hailing services in Ghana. We developed an Android-based AI-powered safety monitoring system that:

- Operates with minimal dependency on internet connectivity
- Detects early warning signs of danger via three key modules:

### Audio Monitoring Module
Detects distress signals like **screams** and **angry speech** using mel spectrograms and lightweight CNNs.

### Facial Expression Recognition Module
Uses the Android front camera and a TFLite model to detect real-time emotions such as **fear**, **anger**, and discomfort.

### Route-Based Geofencing Module
Monitors if the ride deviates from the expected path using GPS, a classification model, and Google Maps API without needing constant internet.

When risks are detected, the app sends alerts to **trusted contacts via SMS**, with fallback logic for escalation. All personal data is encrypted and erased after the trip in compliance with data protection laws.

---

## Deployment Instructions

### Requirements
- Android phone running **Android 8.0 (API 26)** or higher
- Internet access **only required for initial route fetch**
- Location, camera, and microphone permissions enabled

### Setup Steps
1. Clone the GitHub repository:  
      [https://github.com/fremariab/guardian.git](https://github.com/fremariab/guardian.git)

2. Open the project in **Android Studio Ladybug (2024.2.1) or later**

3. Grant the required permissions in the app manifest:
   - `ACCESS_FINE_LOCATION`
   - Camera and microphone

4. Build and deploy the app to an Android device

5. On Launch:
   - Use **Google Places Autocomplete** to set the ride destination
   - Start ride monitoring to activate all safety modules
   - If a threat is detected:
     - App will prompt user to confirm or cancel
     - If no response, SMS alert is automatically sent to a trusted contact

---

## Notable Technologies

- **Mobile Development:** Kotlin, Android Jetpack, Google Maps SDK, Places SDK
- **Audio Processing:** Librosa, JTransform, custom FFT logic
- **Machine Learning:** TensorFlow Lite CNNs, custom classification models
- **Facial Recognition:** CameraX, Google ML Kit
- **Backend APIs:** Google Directions API, Maps API

---

## Data Privacy and Compliance

- All data (audio, video, GPS) is processed **locally on the device**
- No personal data is stored after the ride ends
- Fully compliant with:
  - **Ghana Data Protection Act**
---
