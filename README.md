Skeptik is a collection of data structures and algorithms focused especially on the compression of formal proofs. 

Resolution proofs, in particular, are used by various sat-solvers, smt-solvers and first-order theorem provers, as certificates of correctness for the answers they provide.
These automated deduction tools have a wide range of application areas, from mathematics to software and hardware verification.

By providing smaller resolution proofs that are easier and faster to check, Skeptik aims at improving the reliability of these automated deduction tools and at facilitating the exchange of information between them.


###Usage Instructions###

You must have [SBT](https://github.com/harrah/xsbt/wiki/Getting-Started-Setup) (version >= 0.11.3) installed. 
Then go to Skeptik's root directory using the terminal and simply execute:

```
  sbt run
```

Further instructions, such as necessary command-line arguments, will be shown to you.
If you face any difficulty, do not hesitate to contact us.



###Stats###

* [![Build Status](https://buildhive.cloudbees.com/job/Paradoxika/job/Skeptik/badge/icon)](https://buildhive.cloudbees.com/job/Paradoxika/job/Skeptik/)
* [Ohloh](https://www.ohloh.net/p/Skeptik)


###Developers and Contributors###

 * Bruno Woltzenlogel Paleo (@Ceilican)
 * Joseph Boudou (@Jogo27)


###Websites and Forums###

 * [Skeptik's Main Website](http://paradoxika.github.com/Skeptik/)
 * [Skeptik's Mailinglist for Developers](https://groups.google.com/forum/?fromgroups#!forum/skeptik-dev)
 

###Support###
 
 * Skeptik's development is currently funded by the Austrian Science Fund ([Project P24300](http://www.fwf.ac.at/en/projects/projekt_datenbank.asp))
 * Skeptik was supported by [Google Summer of Code 2012](http://www.google-melange.com/gsoc/project/google/gsoc2012/josephboudou/17001)
 * [YourKit](http://www.yourkit.com/) is an excellent profiler for Scala applications. It supports our open-source project with a free license.