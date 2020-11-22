// 反汇编器实现
$(document).ready(function(){
    var oTxtCode = document.getElementById("textareaCode");
    var oTxtResult = document.getElementById("textareaResult");
    var tip = document.getElementById('tip');
    var msg = document.getElementById('errorMsg');
    $("#disassembleBtn").on("click",function(){
        console.log(oTxtCode);
        var machineCode = document.getElementById("machineCode");
        machineCode.value = "";
        msg.innerHTML = "";
        $.ajax({
            type: "POST",
            data: JSON.stringify({code: $("#textareaCode").val()}),
            dataType: "text",
            contentType: 'application/json;charset=UTF-8',
            url: "/sendMachineCode",
            success: function(data){
                tip.innerText = "";
                data = JSON.parse(data);
                console.log(data);
                if(data['status'] === 0){
                    oTxtResult.innerHTML = data['code'];
                    hljs.highlightBlock(oTxtResult);
                }
                else{
                    // alert(data['errorMsg']);
                    msg.innerHTML = data['code'];
                }
            },
            error: function(){
                tip.innerText = "抱歉，本次请求发送失败，请重试！";
            }
        });
    });

    // 使Tab键能输入
    oTxtCode.addEventListener("keydown", function (event) {
        var index = getTxtCursorPosition();
        var txt = this.value;
        if (event.key !== undefined) {
            if(event.key === "Tab"){
                if (!this.value) this.value= '';
                // alert(index + "," + this);
                // alert(txt);
                this.value = txt.slice(0, index) + "\t" + txt.slice(index);
                // alert(this.value);
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
        console.log("File Btn clicked");
        tip.innerText = "";
        $("#assembleFile").click();
    });

    var file = document.getElementById('assembleFile');
    var fileName = "";
    file.onchange = function(e){
        var files = e.target.files;
        console.log(files);
        var file0 = files[0];
        fileName = file0.name;
        console.log(file0);
        var suffix = fileName.substring(fileName.lastIndexOf('.')+1);
        if(suffix !== 's' && suffix !== 'txt')
        {
            tip.innerText = "无法接收以" + suffix + "为后缀的文件作为输入！";
        }
        else{
            var fileReader = new FileReader();
            fileReader.readAsText(file0,"utf-8");
            fileReader.onerror = function(){
                tip.innerText = "无法读取文件" + fileName + "，请重试！";
            };
            fileReader.onload = function(){
                oTxtCode.value = fileReader.result;
            }
        }
    };

    var clipboard = new ClipboardJS('#copyFileBtn');

    clipboard.on('success', function(e) {
        msg.innerHTML = "汇编代码已成功复制到剪贴板！";
        console.log(e);
    });

    clipboard.on('error', function(e) {
        msg.innerHTML = "复制失败，请重试！";
        console.log(e);
    });

    $("#saveFileBtn").on("click",function () {
        if(oTxtResult.innerHTML){
            $.ajax({
                type: "POST",
                contentType: 'application/json;charset=UTF-8',
                url: "/getAssembleCodeFile",
                success: function (data) {
                    msg.innerHTML = "";
                    console.log(data);
                    // data = JSON.parse(data);
                    // console.log(data);
                    if(data['status'] === 0){
                        //status=0表示机器码文件生成成功
                        var machineCode = document.getElementById("machineCode");
                        machineCode.value = data['code'];
                        console.log(data['code']);
                        var copyFileBtn = document.getElementById('copyFileBtn');
                        copyFileBtn.click();
                        // var dTip = document.getElementById('dTip');
                        // msg.style.display = "block";
                        // msg.innerHTML = "汇编代码已成功复制到剪贴板！"
                    }
                    else{
                        console.log("download failed!");
                    }
                },
                error: function () {
                    msg.innerHTML = "下载MIPS汇编代码文件请求发送失败，请重试！";
                }
            });
        }
    })

});