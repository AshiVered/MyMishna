const fs = require('fs');
const code = fs.readFileSync('hebcal.min.js', 'utf8');
eval(code);
const sedra = new hebcal.Sedra(5785, true); 
const hdate = new hebcal.HDate(10, 7, 5785); // 10 Tishrei 5785 is Yom Kippur (Shabbat)
console.log(sedra.get(hdate));
