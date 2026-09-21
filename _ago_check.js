function ago(ms){if(!ms)return '방금 전';var d=Date.now()-ms;var m=Math.floor(d/60000);if(m<1)return '방금 전';if(m<60)return m+'분 전';var h=Math.floor(m/60);if(h<24)return h+'시간 전';return Math.floor(h/24)+'일 전';}
console.log(ago(Date.now()-3600000));
