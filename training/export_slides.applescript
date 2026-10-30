on run argv
	set inPath to item 1 of argv
	set outPath to item 2 of argv
	tell application "Keynote"
		set d to open POSIX file inPath
		delay 2
		export d to POSIX file outPath as slide images with properties {image format:PNG, image quality:best, skipped slides:false}
		delay 1
		close d saving no
	end tell
	return "ok"
end run
