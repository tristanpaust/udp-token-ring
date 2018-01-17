# udp-token-ring
Forms a ring of clients and passes messages
Takes three input files:
- config.txt, used for assigning a port number, gives information about the total amount of ports, the join and the leave time of a client
- input.txt, contains the post messages that a posted at a certain time
- output.txt, logs the ring formation, election and message posting

Clients enter the network at the time specified in the config file, start finding their next and previous port, then elect a leader (the client with the highest port number) and post messages as soon as the generated token is passed to them.
As soon as a new client joins or an exisitng member leaves, the ring breaks, a new ring is formed and a new token is generated.
