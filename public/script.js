let currentEndpoint = "rooms"
let editingId = null

function loadData(endpoint){

currentEndpoint = endpoint

fetch("http://localhost:8080/"+endpoint)
.then(res=>res.text())
.then(data=>{

let rows = data.trim().split("\n").filter(row => row.trim() !== "")

let tableHead = document.getElementById("tableHead")
let tableBody = document.getElementById("tableBody")

tableHead.innerHTML=""
tableBody.innerHTML=""

// Check if no data present
if(rows.length === 0){
    
    let headers=[]
    
    if(endpoint==="rooms"){
        headers=["ID","Room Name","Type","Capacity","Actions"]
    }
    if(endpoint==="users"){
        headers=["ID","Name","Email","Actions"]
    }
    if(endpoint==="buildings"){
        headers=["ID","Name","Location","Actions"]
    }
    if(endpoint==="bookings"){
        headers=["Booking ID","Room","User","Actions"]
    }
    
    headers.forEach(h=>{
        let th=document.createElement("th")
        th.textContent=h
        tableHead.appendChild(th)
    })
    
    // Show "No data" message in the middle with Add button
    let tr = document.createElement("tr")
    tr.innerHTML = `
        <td colspan="${headers.length}" style="text-align: center; padding: 30px;">
            <p style="font-size: 18px; color: #666; margin-bottom: 15px;">No data present</p>
            <button onclick="openForm(currentEndpoint)" style="padding: 10px 20px; font-size: 16px; cursor: pointer; background-color: #4CAF50; color: white; border: none; border-radius: 4px;">Add</button>
        </td>
    `
    tableBody.appendChild(tr)
    return
}

let headers=[]

if(endpoint==="rooms"){
headers=["ID","Room Name","Type","Capacity","Actions"]
}

if(endpoint==="users"){
headers=["ID","Name","Email","Actions"]
}

if(endpoint==="buildings"){
headers=["ID","Name","Location","Actions"]
}

if(endpoint==="bookings"){
headers=["Booking ID","Room","User","Actions"]
}

headers.forEach(h=>{
let th=document.createElement("th")
th.textContent=h
tableHead.appendChild(th)
})

rows.forEach(row=>{

if(row==="") return

let cols=row.split("|")
let tr=document.createElement("tr")

// BOOKINGS TABLE
if(endpoint==="bookings"){

tr.innerHTML=`
<td>${cols[0]}</td>
<td>${cols[3]}</td>
<td>${cols[4]}</td>
<td>
<button onclick="editBooking(${cols[0]},${cols[1]},${cols[2]})">Edit</button>
<button onclick="deleteRow(${cols[0]})">Delete</button>
</td>
`

tableBody.appendChild(tr)
return
}

// OTHER TABLES
cols.forEach(c=>{
let td=document.createElement("td")
td.textContent=c
tr.appendChild(td)
})

let action=document.createElement("td")
let id = cols[0]
let field1 = cols.length > 1 ? cols[1] : ""
let field2 = cols.length > 2 ? cols[2] : ""
let field3 = cols.length > 3 ? cols[3] : ""

action.innerHTML = '<button onclick="editNormal(\'' + id + '\',\'' + escapeQuote(field1) + '\',\'' + escapeQuote(field2) + '\',\'' + escapeQuote(field3) + '\')">Edit</button> <button onclick="deleteRow(' + id + ')">Delete</button>'

tr.appendChild(action)

tableBody.appendChild(tr)

})

})
}
function editNormal(id, field1, field2, field3){

editingId = id

openForm(currentEndpoint, id)

setTimeout(()=>{

if(document.getElementById("field1"))
document.getElementById("field1").value = field1

if(document.getElementById("field2"))
document.getElementById("field2").value = field2

if(document.getElementById("field3"))
document.getElementById("field3").value = field3

},100)

}

function openForm(endpoint,id=null){

editingId=id

let modal=document.getElementById("modal")
let form=document.getElementById("formFields")
let title=document.getElementById("modalTitle")

form.innerHTML=""

if(endpoint==="rooms"){

title.innerText="Room Form"

form.innerHTML=`
<input id="field1" placeholder="Room Name">
<input id="field2" placeholder="Type">
<input id="field3" placeholder="Capacity">
`
}

if(endpoint==="users"){

title.innerText="User Form"

form.innerHTML=`
<input id="field1" placeholder="Name">
<input id="field2" placeholder="Email">
`
}

if(endpoint==="buildings"){

title.innerText="Building Form"

form.innerHTML=`
<input id="field1" placeholder="Name">
<input id="field2" placeholder="Location">
`
}

if(endpoint==="bookings"){

title.innerText="Booking Form"

form.innerHTML=`

<label>Room</label>
<select id="field1"></select>

<label>User</label>
<select id="field2"></select>

`

loadRooms()
loadUsers()

}

modal.style.display="flex"

}


function closeModal(){

document.getElementById("modal").style.display="none"
editingId=null

}


function validateForm(){
    let inputs = document.querySelectorAll("#formFields input,#formFields select")
    let errorMessage = ""
    
    // Room validation
    if(currentEndpoint === "rooms"){
        let field1 = document.getElementById("field1")
        let field2 = document.getElementById("field2")
        let field3 = document.getElementById("field3")
        
        if(!field1.value.trim()){
            errorMessage = "Room Name is required"
        } else if(!field2.value.trim()){
            errorMessage = "Type is required"
        } else if(!field3.value.trim()){
            errorMessage = "Capacity is required"
        } else if(isNaN(field3.value) || parseInt(field3.value) <= 0){
            errorMessage = "Capacity must be a positive number"
        }
    }
    
    // User validation
    if(currentEndpoint === "users"){
        let field1 = document.getElementById("field1")
        let field2 = document.getElementById("field2")
        
        if(!field1.value.trim()){
            errorMessage = "Name is required"
        } else if(!field2.value.trim()){
            errorMessage = "Email is required"
        } else if(!isValidEmail(field2.value)){
            errorMessage = "Please enter a valid email address"
        }
    }
    
    // Building validation
    if(currentEndpoint === "buildings"){
        let field1 = document.getElementById("field1")
        let field2 = document.getElementById("field2")
        
        if(!field1.value.trim()){
            errorMessage = "Name is required"
        } else if(!field2.value.trim()){
            errorMessage = "Location is required"
        }
    }
    
    // Booking validation
    if(currentEndpoint === "bookings"){
        let field1 = document.getElementById("field1")
        let field2 = document.getElementById("field2")
        
        if(!field1.value){
            errorMessage = "Please select a Room"
        } else if(!field2.value){
            errorMessage = "Please select a User"
        }
    }
    
    if(errorMessage){
        alert(errorMessage)
        return false
    }
    return true
}

function isValidEmail(email){
    let emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
}

function escapeQuote(str){
    if(str === undefined || str === null) return ""
    return String(str).replace(/'/g, "\\'")
}

function submitForm(){

// Validate form before submitting
if(!validateForm()){
    return
}

let inputs=document.querySelectorAll("#formFields input,#formFields select")

let data={}

inputs.forEach((i,index)=>{
data["field"+(index+1)] = i.value
})

if(editingId !== null){
data["id"] = editingId
}

console.log("Editing ID:", editingId)
console.log("Request Method:", editingId ? "PUT":"POST")
console.log(data)

fetch("http://localhost:8080/"+currentEndpoint,{
method: editingId ? "PUT":"POST",
headers:{"Content-Type":"application/json"},
body:JSON.stringify(data)
})
.then(()=>{
closeModal()
loadData(currentEndpoint)
})

}

function deleteRow(id){

fetch("http://localhost:8080/"+currentEndpoint+"?id="+id,{
method:"DELETE"
})
.then(()=>loadData(currentEndpoint))

}


// EDIT FOR ROOMS / USERS / BUILDINGS
function editRow(row){

let cols = row.split("|")

editingId = cols[0]

openForm(currentEndpoint, editingId)

setTimeout(()=>{

let f1=document.getElementById("field1")
let f2=document.getElementById("field2")
let f3=document.getElementById("field3")

if(f1) f1.value = cols[1]
if(f2) f2.value = cols[2]
if(f3) f3.value = cols[3]

},100)

}


// EDIT FOR BOOKINGS
function editBooking(id,roomId,userId){

editingId = id

openForm("bookings", id)

setTimeout(()=>{

document.getElementById("field1").value = roomId
document.getElementById("field2").value = userId

},200)

}

// LOAD ROOMS DROPDOWN
function loadRooms(){

fetch("http://localhost:8080/rooms")
.then(res=>res.text())
.then(data=>{

let rows=data.trim().split("\n")
let select=document.getElementById("field1")

select.innerHTML=""

rows.forEach(r=>{

let cols=r.split("|")

let option=document.createElement("option")
option.value=cols[0]
option.textContent=cols[1]

select.appendChild(option)

})

})

}


// LOAD USERS DROPDOWN
function loadUsers(){

fetch("http://localhost:8080/users")
.then(res=>res.text())
.then(data=>{

let rows=data.trim().split("\n")
let select=document.getElementById("field2")

select.innerHTML=""

rows.forEach(r=>{

let cols=r.split("|")

let option=document.createElement("option")
option.value=cols[0]
option.textContent=cols[1]

select.appendChild(option)

})

})

}


loadData("rooms")

