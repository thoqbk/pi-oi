# Pi-oi: a Tool for Finding Local IP Address of Your Raspberry Pi

## System requirements
1. Java 7+

## How to use
Download latest version at: https://raw.githubusercontent.com/thoqbk/pi-oi/master/pi-oi.jar

Run pi-oi using command: `java -jar pi-oi.jar`

## Introduction
Raspberry Pi machine is the most interesting thing I have this year. Why is it interesting? I think because it’s small and very powerful, it can run any program written in my favourite languages like Java, Javascript (Nodejs), PHP. It’s awesome machine which I can’t imagine when I was a student

I like to write some small applications in Raspberry Pi to control my home appliances remotely. And the first thing I must do before working with Raspberry Pi is open a SSH connection to it. It means that I must know the local IP address of it. I have tried using some tools to scan all devices in my local network to find the dynamic IP that the router set for my Raspberry Pi including arp, ping, nmap, Angry IP scanner (on Windows), Smart IP scanner (on Windows) but all of them did not satisfy me because:
* The command line tools (arp, ping, nmap) usually couldn’t find my RPi and they did not work like some instructions I found on the Internet
* IP scanner tool (Angry IP scanner, Smart IP scanner): could scan all machines on my local network in about 3 minutes and after getting the list of IP addresses I must check each IP consequently by attempt to open ssh connection to port 22 to know whether or not it’s my Raspberry Pi. And this is really not convenient way!

So I decided to develop my own algorithm to resolve all these stuffs. The algorithm uses two following knowledge:

1. In a local network, the dynamic IP addresses usually are provided in continuous ranges. For example, 192.168.1.100, 192.168.1.101 … So if I find out an active IP address, I will continue expanding the scanning range with assumption the other active address could be very near the current address.
2. Raspberry Pi usually installs a Linux OS and runs a SSH server at port 22. If I try to open a socket connection to this port, it will response information about its SSH server version and OS, for example: “ SSH-2.0-OpenSSH_6.0p1 Debian-4”, “ SSH-2.0-OpenSSH_6.6.1p1 Ubuntu-2ubuntu2.3”, basing on this information we can guess the Pi’s address, for example my Pi installs Debian OS so the first one is my Pi

## Pi-oi algorithm
Following is the pseudo code of the algorithm for single thread scanning:
```java
myLocalAddresses <— get local IP addresses //example: 192.168.1.23
for each myLocalAddress in myLocalAddresses do
    networkPrefix <— get network prefix of myLocalAddress // example: 192.168.1 
    initPivot <— get host number of myLocalAddress // example: 23
    pendingHostNumbers <— [1,255] - {initPivot} // pending for scanning    
    pivot, scanningRange <— null
    
    while (pendingHostNumbers is not empty) OR (scanningRange is not null) OR (pivot is not null) do
        if scanningRange is null and pivot is null then
            scanningRange <— pick range from pendingHostNumbers around initPivot
        else if scanningRange is not empty then
            scan all ip addresses in scanningRange and print out result if found a machine open SSH port
            if last ip address in scanningRange is active
                scanningRange <— pick a range from pendingHostNumbers around scanningRange.last
            else if first ip address in scanningRange is active
                scanningRange <— pick a range in pendingHostNumbers around scanningRange.first
            else
                scanningRange <— null
                pivot <— pick a new pivot from pendingHostNumbers
            end if
        else if pivot is not null then
            scan pivot
            if pivot is active then
                scanningRange <— pick a range from pendingHostNumbers around pivot
            else
                pivot <— pick a new pivot from pendingHostNumbers
            end if
        else
            pivot <— pick a new pivot from pendingHostNumbers
            scanningRange <— null
        end if
    end of while
end of for
```
In the implementation I split the range [1,255] in 5 continuous parts and start a 5-threads executor-service for scanning faster.

## Implementation and test result
I used Java to implement this algorithm and tested in some local networks. Result is Pi-oi usually found my Raspberry Pi in less than 30 seconds. Following are shot screens of my tests:

Test in my home:

![test in my home](https://github.com/thoqbk/pi-oi/blob/master/resources/test-in-my-home.png)

Test at my company:

![test at my company](https://github.com/thoqbk/pi-oi/blob/master/resources/test-at-my-company.png)

## Author and contact
[ThoQ Luong](https://github.com/thoqbk/)

Email: thoqbk@gmail.com

## License

The MIT License (MIT)