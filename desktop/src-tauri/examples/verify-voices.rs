//! Explicit native speech verification; never edits the user's library.
use std::{path::PathBuf, sync::atomic::Ordering};
use vern_desktop_lib::{
    speech::Speech,
    transfer::wav_bytes,
    voices::{descriptor, Voices},
};
fn main() -> Result<(), String> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if args.len() != 2 {
        return Err("Usage: verify-voices <existing-model-directory> <output-directory>".into());
    }
    let voices = Voices::new(PathBuf::from(&args[0]))?;
    let output = PathBuf::from(&args[1]);
    std::fs::create_dir_all(&output).map_err(|e| e.to_string())?;
    let catalogue = voices.list()?;
    #[cfg(target_os = "windows")]
    if !catalogue
        .iter()
        .any(|voice| voice.family == "Windows" && voice.state == "installed")
    {
        return Err(format!(
            "WinRT returned no usable installed Windows voices: {}",
            serde_json::to_string(&catalogue).map_err(|e| e.to_string())?
        ));
    }
    println!(
        "{}",
        serde_json::to_string_pretty(&catalogue).map_err(|e| e.to_string())?
    );
    let speech = Speech::default();
    let mut generation = 1;
    let mut ordered = catalogue.iter().collect::<Vec<_>>();
    ordered.sort_by_key(|voice| voice.family != "Windows");
    for voice in ordered.into_iter().filter(|voice| {
        voice.state == "installed"
            && (voice.family == "Windows"
                || [
                    "kokoro_af_heart",
                    "kokoro_af_bella",
                    "kokoro_am_michael",
                    "kokoro_bf_emma",
                    "kokoro_bm_george",
                ]
                .contains(&voice.id.as_str()))
    }) {
        speech.generation.store(generation, Ordering::SeqCst);
        for rate in [1.0, 1.5] {
            let audio = speech.synthesize(
                &voices,
                &voice.id,
                "A quiet page, a familiar voice. The next chapter is waiting.",
                rate,
                generation,
            )?;
            if audio.samples.is_empty() || !audio.samples.iter().any(|sample| sample.abs() > 0.001)
            {
                return Err(format!("{} produced silent audio", voice.name));
            }
            println!(
                "VERIFIED {} / {}: {} samples at {} Hz, speed {}",
                voice.id,
                voice.name,
                audio.samples.len(),
                audio.sample_rate,
                rate
            );
            std::fs::write(
                output.join(format!("{}-{rate}.wav", voice.id)),
                wav_bytes(audio)?,
            )
            .map_err(|e| e.to_string())?;
        }
        speech.cancel(generation + 1);
        if speech
            .synthesize(
                &voices,
                &voice.id,
                "This request is obsolete.",
                1.0,
                generation,
            )
            .is_ok()
        {
            return Err("Obsolete speech was not rejected".into());
        }
        generation += 2;
    }
    assert_eq!(descriptor("kokoro_af_heart")?.sid, 3);
    assert_eq!(descriptor("kokoro_bf_emma")?.sid, 21);
    if let Some(voice) = catalogue
        .iter()
        .find(|voice| voice.family == "Windows" && voice.state == "installed")
    {
        use std::{sync::Arc, thread, time::Duration};
        let speech = Arc::new(speech);
        speech.generation.store(generation, Ordering::SeqCst);
        let cancel = speech.clone();
        let timer = thread::spawn(move || {
            thread::sleep(Duration::from_millis(40));
            cancel.cancel(generation + 1);
        });
        let result = speech.synthesize(
            &voices,
            &voice.id,
            &"A long sentence is being synthesized for cancellation testing. ".repeat(50),
            1.0,
            generation,
        );
        timer
            .join()
            .map_err(|_| "Cancellation test thread failed")?;
        match result {
            Ok(_) => return Err("Cancelled Windows synthesis returned audio".into()),
            Err(message) => println!("VERIFIED Windows cancellation: {message}"),
        }
    }
    Ok(())
}
