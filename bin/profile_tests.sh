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
