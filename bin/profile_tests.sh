#!/usr/bin/env ruby
# -*- coding: utf-8 -*-

`rm -rf target`
`lein cljsbuild once 2>&1 > /dev/null`

puts "Advanced tests..."

times = []
10.times do |i|
  out = `bin/node target/cljs/node_adv/tests.js`
  out =~ /Elapsed time: (\d+) msecs/
  time = $1.to_i
  times << time.to_f
end
p times
avg = times.reduce(:+) / times.length
stddev = Math.sqrt(times.map{|x|x - avg}.map{|x|x*x}.reduce(:+)/times.length)
puts("%dms ± %dms" % [avg.to_i,stddev.to_i])

puts "No optimizations tests..."

times = []
10.times do
  out = `bin/node resources/run.js`
  out =~ /Elapsed time: (\d+) msecs/
  time = $1.to_i
  times << time.to_f
end
p times
avg = times.reduce(:+) / times.length
stddev = Math.sqrt(times.map{|x|x - avg}.map{|x|x*x}.reduce(:+)/times.length)
puts("%dms ± %dms" % [avg.to_i,stddev.to_i])


# Master

# Advanced tests...
# "Elapsed time: 19562 msecs"
# "Elapsed time: 19888 msecs"
# "Elapsed time: 20607 msecs"
# "Elapsed time: 24648 msecs"
# "Elapsed time: 23355 msecs"
# "Elapsed time: 22441 msecs"
# "Elapsed time: 23319 msecs"
# "Elapsed time: 22597 msecs"
# "Elapsed time: 19639 msecs"
# "Elapsed time: 20823 msecs"
# No optimizations tests...
# "Elapsed time: 23470 msecs"
# "Elapsed time: 23998 msecs"
# "Elapsed time: 22155 msecs"
# "Elapsed time: 27810 msecs"
# "Elapsed time: 26126 msecs"
# "Elapsed time: 23251 msecs"
# "Elapsed time: 26021 msecs"
# "Elapsed time: 21121 msecs"
# "Elapsed time: 23696 msecs"
# "Elapsed time: 22945 msecs"

# cljs-random
# Advanced tests...
# "Elapsed time: 32869 msecs"
# "Elapsed time: 23479 msecs"
# "Elapsed time: 20210 msecs"
# "Elapsed time: 27654 msecs"
# "Elapsed time: 20924 msecs"
# "Elapsed time: 20662 msecs"
# "Elapsed time: 21588 msecs"
# "Elapsed time: 18908 msecs"
# "Elapsed time: 26046 msecs"
# "Elapsed time: 18573 msecs"
# No optimizations tests...
# "Elapsed time: 59466 msecs"
# "Elapsed time: 37126 msecs"
# "Elapsed time: 37979 msecs"
# "Elapsed time: 36662 msecs"
# "Elapsed time: 38832 msecs"
# "Elapsed time: 33531 msecs"
# "Elapsed time: 36389 msecs"
# "Elapsed time: 34283 msecs"
# "Elapsed time: 37292 msecs"
# "Elapsed time: 37396 msecs"








# Master
# Advanced
# 19562,19888,20607,24648,23355,22441,23319,22597,19639,20823
# 21687 ± 1809
# No optimizations
# 23470,23998,22155,27810,26126,23251,26021,21121,23696,22945
# 24059 ± 2022


# cljs-random
# Advanced
# 32869,23479,20210,27654,20924,20662,21588,18908,26046,18573
# 23091 ± 4526
# No optimizations
# 59466,37126,37979,36662,38832,33531,36389,34283,37292,37396
# 38895 ± 7403
