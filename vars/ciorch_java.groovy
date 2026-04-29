def call(Map args = [:]) {
    ciorch([adapter: 'java'] + args)
}
