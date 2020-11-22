// 汇编器实现
$(document).ready(function(){
    hljs.initHighlightingOnLoad();
    var binMachineCode = "bin", hexMachineCode = "hex";
    var oTxtCode = document.getElementById("textareaCode");
    var oTxtResult = document.getElementById("textareaResult");
    var tip = document.getElementById('tip');
    var dTip = document.getElementById('dTip');
    var code = document.getElementById("highlightCode");
    $("#assembleBtn").on("click",function(){
        console.log(oTxtCode);
        var machineCode = document.getElementById("machineCode");
        machineCode.value = "";
        dTip.innerHTML = "";
        $.ajax({
            type: "POST",
            data: JSON.stringify({code: $("#textareaCode").val()}),
            dataType: "text",
            contentType: 'application/json;charset=UTF-8',
            url: "/sendMIPSCode",
            success: function(data){
                tip.innerText = "";
                data = JSON.parse(data);
                console.log(data);
                binMachineCode = data['bin'];
                hexMachineCode = data['hex'];
                if(data['status'] === 0){
                    if(!$("#radix").val())
                        $("#radix").val('2');
                    updateResultContent();
                }
                else{
                    // alert(data['errorMsg']);
                    oTxtResult.value = data['errorMsg'];
                }
            },
            error: function(){
                tip.innerText = "抱歉，本次请求发送失败，请重试！";
            }
        });
    });

    var radixBtn = document.getElementById('radixChangeBtn');
    radixBtn.onclick=function(){
        if(radixBtn.innerHTML ==="<span class=\"glyphicon glyphicon-adjust\"></span> 进制切换(2)") {
            radixBtn.innerHTML = "<span class=\"glyphicon glyphicon-adjust\"></span> 进制切换(16)";
            $("#radix").val('16');
        }
        else{
            radixBtn.innerHTML = "<span class=\"glyphicon glyphicon-adjust\"></span> 进制切换(2)";
            $("#radix").val('2');
        }
        updateResultContent();
    };

    function updateResultContent(){
        var radixNum = $("#radix").val();
        console.log(radixNum);
        console.log(radixNum==='2');
        if(radixNum === '2'){
            oTxtResult.value = binMachineCode;
        }
        else{
            oTxtResult.value = hexMachineCode;
        }
    }

    // 使Tab键能输入
    oTxtCode.addEventListener("keydown", function (event) {
        var index = getTxtCursorPosition();
        var txt = this.value;
        if (event.key !== undefined) {
            if(event.key === "Tab"){
                if (!this.value) this.value= '';
                this.value = txt.slice(0, index) + "\t" + txt.slice(index);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
            if(event.key === "Enter"){
                if (!this.value) this.value= '';
                this.value = txt.slice(0, index) + "\r\n" + txt.slice(index);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        } else if (event.keyCode !== undefined) {
            if (event.keyCode === 9) {
                if (!this.value) this.value= '';
                txt.replace(txt.substring(0,index), txt.substring(0,index)+"\t");
                this.value = txt;
                alert(index + "," + this);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        }
    }, true);

    oTxtCode.addEventListener("keyup",function () {
        code.innerHTML = oTxtCode.value;
        hljs.highlightBlock(code);
    });

    function getTxtCursorPosition(){
        var textArea = document.getElementById("textareaCode");
        var cursorPosition=0;
        if(textArea.selectionStart){//非IE
            cursorPosition= textArea.selectionStart;
        }else{//IE
            try{
                var range = document.selection.createRange();
                range.moveStart("character",-textArea.value.length);
                cursorPosition=range.text.length;
            }catch(e){
                cursorPosition = 0;
            }
        }
        return cursorPosition;
    }

    $("#openFileBtn").on("click",function(){
        tip.innerText = "";
        //引发文件读取事件
        $("#assembleFile").click();
    });

    //文件读取处理
    var file = document.getElementById('assembleFile');
    var fileName = "";
    file.onchange = function(e){
        var files = e.target.files;
        //选取要读取的文件
        var file0 = files[0];
        fileName = file0.name;
        var suffix = fileName.substring(fileName.lastIndexOf('.')+1);
        if(suffix !== 's' && suffix !== 'txt')
        {
            tip.innerText = "无法接收以" + suffix + "为后缀的文件作为输入！";
        }
        else{
            var fileReader = new FileReader();
            // 发送异步请求
            // 0.使用readAsText方法（读取结果普通文本）
            fileReader.readAsText(file0,"utf-8");
            // 读取失败
            fileReader.onerror = function(){
                tip.innerText = "无法读取文件" + fileName + "，请重试！";
            };
            // 读取成功，数据保存在fileReader对象的result属性中
            fileReader.onload = function(){
                oTxtCode.value = fileReader.result;
                //同步写到code元素中并进行语法高亮
                code.innerHTML = oTxtCode.value;
                hljs.highlightBlock(code);
            }
        }
    };

    var clipboard = new ClipboardJS('#copyFileBtn');

    clipboard.on('success', function(e) {
        dTip.innerHTML = "机器码已成功复制到剪贴板！";
        console.log("success");
        console.log(e);
    });

    clipboard.on('error', function(e) {
        dTip.innerHTML = "复制失败，请重试！";
        console.log("error");
        console.log(e);
    });

    //下载文件请求处理
    $("#saveFileBtn").on("click",function () {
        if(oTxtResult.value){
            $.ajax({
                type: "POST",
                //根据进制选择下载的机器码进制格式
                data: JSON.stringify({radix: $("#radix").val()}),
                dataType: "text",
                contentType: 'application/json;charset=UTF-8',
                url: "/getMachineCodeFile",
                success: function (data) {
                    dTip.innerHTML = "";
                    data = JSON.parse(data);
                    if(data['status'] === 0){
                        //status=0表示机器码文件生成成功
                        var machineCode = document.getElementById("machineCode");
                        machineCode.value = data['code'];
                        // var foo = document.getElementById("foo");
                        machineCode.value = data['code'];
                        console.log(data['code']);
                        var copyFileBtn = document.getElementById('copyFileBtn');
                        copyFileBtn.click();
                    }
                    else{
                        dTip.innerHTML = "机器码复制失败，请重试！";
                    }
                },
                error: function () {
                    dTip.innerHTML = "复制机器码请求发送失败，请重试！";
                }
            });
        }
    });
});