# SlideExtractor

A tool I've created to help me create slides out of lesson notes.

This is becoming more useful, so I've now extended this to work with more than just one lesson.

This is an intermediate point: As it stands it works with two different projects of mine.
Shortly I'll make it a more useful utility.

BTW, an interesting idea from software carpentry:

##Live log

<a href="https://douglatornell.github.io/2015-09-22-ubc/">The University of British Columbia</a>
had a live log of shell commands ona web page. This is how they did it:

```bash
cat > live-log.sh <<- EndOfMessage
HISTTIMEFORMAT="%Y-%m-%d %H:%M:%S $ "
PROMPT_COMMAND="history 1 >> ~/history.txt; rsync -qz ~/history.txt URL_OF_PUBLIC_REMOTE_SERVER:PATH/TO/PUBLIC/DIRECTORY/history.txt"
EndOfMessage
```

Then `source live-log.sh`

Note:

* `HISTTIMEFORMAT` adds timestamps to the commands
* [`PROMPT_COMMAND`](http://www.tldp.org/HOWTO/Bash-Prompt-HOWTO/x264.html) does the work
* `rsync` can be replaced by any tool that synchronises files between machines.

