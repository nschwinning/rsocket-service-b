## Overview

RSocket server to be used together with the [client](https://github.com/nschwinning/rsocket-service-a).
The service provides RSocket interfaces to fetch data.

## Example Usage

Install [rsc](https://github.com/making/rsc) on command line:

```
brew install making/tap/rsc
```

Use the following command to test the application:

```
rsc --stream --route=quotes --data=10 tcp://localhost:9391
```

Or get one quote:

```
rsc --request --route=quote tcp://localhost:9391
```