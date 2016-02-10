//TODO support characters. Variant doesnt know anything about them.
types=[
	["String",["java.lang.String","String"],"String"],
	["Byte","byte","SByte"],
	//["Char","char","Byte"],
	["Short","short","Int16"],
	["Integer","int","Int32"],
	["Long","long","Int64"],
	["Double","double","Double"],
	["Float","float","Float"],
	["Boolean","boolean","Boolean"]
]
prims = [
	"boolean" : 
		["rand" : "RANDOM.nextBoolean()"],
	"byte": 
		["rand" : "(byte) RANDOM.nextInt(Byte.MAX_VALUE + 1)"],
	"short": 
		["rand" : "(short) RANDOM.nextInt(Short.MAX_VALUE + 1)"],
	/*"char": 
		["rand" : "(char) RANDOM.nextInt(Character.MAX_VALUE + 1)"],*/
	"int": 
		["rand" : "RANDOM.nextInt()"],
	"long": 
		["rand" : "RANDOM.nextLong()"],
	"float": 
		["rand" : "RANDOM.nextFloat()"],
	"double": 
		["rand" : "RANDOM.nextDouble()"],
	"String":
		["rand" : "RandomStringUtils.random(RANDOM.nextInt(256))"]
]

