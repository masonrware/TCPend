#!/usr/bin/expect -f
spawn ssh -X mininet@mininet-09.cs.wisc.edu
expect "mininet@mininet-09.cs.wisc.edu's password:"
send "szedNKRF\r"
interact
