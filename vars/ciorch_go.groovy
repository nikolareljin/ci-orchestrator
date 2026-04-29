def call(Map args = [:]) {
    ciorch([adapter: 'go'] + args)
}
