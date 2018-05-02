# OCR in the Cloud
In this project we have implemneted a real-world application to distributively apply OCR algorithms on images.
we display each image with its recognized text on a webpage.

## Team
We are two students and we implemented the project together.
Our names:

Tal Baum

Stas Radchenko

## Instructions 
There are a few steps we have to follow in order to run the application smoothly.
First ,we have to upload the following zip files to our bucket:

1.manager.zip

2.worker.zip

Second, we will create a bucket through AWS console. in this case we used the bucket "talstas".

Now, we need to run this line in the terminal with the names of our input files and the n we chose.
```
java -jar Ass1.jar inputFileName n
```
## How the program works:
Local Application:
* The localapp creates two queues: LocalToManager and ManagerToLocal.
* The localapp uploads the input file of the amazon reviews to the s3.
* The localapp sends a message to the queue LocalToManager.
(this queue used to send messages between the localApp to the manager.
The local app send a message in this format: 
new task + numOfImagesPerWorker + key (which num of images per worker and key are parsed from local app argumetns).
 * After sending the message the local app is waiting to get the message about the summary-file from manager.
 * When the local app gets this message, it downloads the output files from s3, and create an html file presenting the pics and their ocr text.
 
 Manager:
* open 2 queues Manager2Worker queue and Worker2Manager queue.
* The manager is "listening" to the "localToManager" queue in busy-wait form. and will wait for new message.
* when the manager gets a new message he will:
   - download the file from s3
   - parse the arguments from the message -key and num of images per worker.
   - creates a new smaller messages(for each URL in the file) to send to the Manager2Worker queue.
   - after each "numOfImagesPerWorker" he will intiaite and create a worker instance.
   - will count how many messages were sent to the working while using the variable numOfURLs
* the manager will listen to "Worker2Manager" queue, and for each response he will save the response at allrespones list.
* after getting enough responses the manage will create the summary file from all the responses.
* the manager will shut down and closes all the workers instances.
* The manager will upload the file to s3, and send a message "done task" to local app using ManagerToLocal queue.

Worker:
* Opened by the manager.
* The worker parse the url from the Message they got from the Manager at Manager2Worker queue.
* The worker download the image spesificed in the URL and apply OCR actions on it.
* The worker send the text he got to The Manager through Worker2Manager queue.
* The worker delete the message from the queue.

## Q&A
**Question:** Did you think for more than 2 minutes about security?

**Answer:** We took security very seriously. We never hard coded the credentials in the program ,
we are using the EnvironmentVariableCredentialsProvider to get the credentials in the localApp.
To run the instances remotely , we use InstanceProfileCredentialsProvider and IAM roles (for manager and worker).
In this way ,we never expose our credentials , because we never send them in text or in any kind of file.

**Question:** Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, is it scalable?
**Answer:**  

**Question:** What about persistence? What if a node dies? What if a node stalls for a while? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!

**Answer:** 


**Question:** Threads in your application, when is it a good idea? When is it bad?

**Answer:**

There is no need to use thread in the workers, any worker gets only one message, so if we want the messages to being care of more fast we need to open more workers.
Thread in the workers will give us nothing because all the action must occur in a certain order.
It's also a bad idea to use threads in localapp, because it can cause us problems with the download of the input files due to cpu stealing time , and the running time won't improve , and maybe even will get worse.

**Question:** Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.

**Answer:**


**Question:** Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.

**Answer:**


**Question:** Did you manage the termination process? Be sure all is closed once requested!

**Answer:** 
Once certain Local application recieves an task done from the manager, it proceedes to download the summary file that the manager built.
After the download sequence the Local application builts the HTML output file according to the summary file, at the end the local application terminates the s3 conection. As for the manager after finishing one task from certain local application it proceedes on shuting down the instances of the workers that he created. after the sequnce finished the manager proceedes to "listen" for another tasks from another/same local application.

**Question:** Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!

**Answer:**
yes. Moreover if more than 1 app will try to create the manager, it will not succeed,
it will get a message that say :" Manager is already Defined", and we will proceed to send an task to already existing manager, The manager will take care one task at time, meaning that after the manager took care of one task from local application it will proceed to take care of another.
**need to add?**

**Question:** Are all your workers working hard? Or some are slacking? Why?

**Answer:**
No. not all the workers work the same, some of them works on a big amount of messages and some of them works on less.
We think it's because the workers are not created at the same time.

**Question:** Is your manager doing more work than he's supposed to? Have you made sure each part of your system has properly defined tasks? Did you mix their tasks? Don't!

**Answer:**
The Manager tasks are defined above, it doing exactly what it suppose to do.
He is not supposed to worry about How the OCR is done or what the text is-that's the workers task.
He just compose a summary file and upload it to s3.

**Question:** are you sure you understand what distributed means? Is there anything in your system awaiting another?

**Answer:**
A distributed system: (by WIKIPEDIA: "is a model in which components located on networked computers communicate and coordinate their actions by passing messages")
as we can see in our program The local application create a instance that running on other computer and communicate with it with messages.
The manager is a computer that belongs to a computer network on amazon.
The manager uploads more computers from amazon network- the workers, each worker works on a different computer and they communicate only with the manager with messages.
All the computers work together on the same mission, so the mission distributed between all of them.
The local-app is waiting to the manager to create all its output files.
The manager is waiting to the workers to create all the review response for the files that he sent.
All the computers are independent.

